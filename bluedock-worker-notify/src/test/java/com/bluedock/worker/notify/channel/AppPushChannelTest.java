package com.bluedock.worker.notify.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.worker.notify.push.AppPushDelayQueue;
import com.bluedock.worker.notify.repo.AppPushAliasDeliveryRepository;
import com.bluedock.worker.notify.repo.AppPushLogRepository;
import com.bluedock.worker.notify.repo.DialogMuteCheckRepository;
import com.bluedock.worker.notify.repo.MessageReadCheckRepository;
import com.bluedock.worker.notify.repo.NotifySettingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppPushChannelTest {
  @Mock NotifySettingRepository settings;
  @Mock AppPushAliasDeliveryRepository aliases;
  @Mock StringRedisTemplate redis;
  @Mock AppPushDelayQueue delayQueue;
  @Mock MessageReadCheckRepository reads;
  @Mock DialogMuteCheckRepository mutes;
  @Mock AppPushLogRepository logs;

  AppPushChannel channel;

  @BeforeEach
  void setUp() {
    Map<String, Object> cfg = new LinkedHashMap<>();
    cfg.put("open", "open");
    cfg.put("iosKey", "ik");
    cfg.put("iosSecret", "is");
    cfg.put("androidKey", "");
    cfg.put("androidSecret", "");
    cfg.put("aliasType", "bluedock");
    cfg.put("productionMode", "true");
    when(settings.load(eq(NotifySettingNames.APP_PUSH), any())).thenReturn(cfg);
    channel =
        new AppPushChannel(
            settings, aliases, redis, new ObjectMapper(), delayQueue, reads, mutes, logs);
  }

  @Test
  void deliver_pcActive_enqueuesDelay() {
    when(redis.hasKey(RedisKeys.pcActive(9L))).thenReturn(true);
    when(mutes.mutedUserIds(eq(7L), anyList())).thenReturn(Set.of());
    NotifySendEvent event =
        new NotifySendEvent(
            "e1",
            NotifySendEvent.CHANNEL_PUSH,
            List.of(9L),
            "Hi",
            "body",
            Map.of("messageId", 100L, "dialogId", 7L));

    channel.deliver(event);

    verify(delayQueue).enqueue(eq("e1"), eq(List.of(9L)), eq("Hi"), eq("body"), any());
    verify(aliases, never()).listActive(anyList());
    verify(logs)
        .insert(
            eq(9L),
            eq(""),
            eq(""),
            eq("Hi"),
            eq("body"),
            isNull(),
            isNull(),
            eq("delayed"),
            eq("pc_active"),
            eq("e1"),
            eq(100L),
            eq(7L));
  }

  @Test
  void deliver_skipsMuted() {
    when(mutes.mutedUserIds(eq(7L), anyList())).thenReturn(Set.of(9L));
    NotifySendEvent event =
        new NotifySendEvent(
            "e1",
            NotifySendEvent.CHANNEL_PUSH,
            List.of(9L),
            "Hi",
            "body",
            Map.of("messageId", 100L, "dialogId", 7L));

    channel.deliver(event);

    verify(delayQueue, never()).enqueue(anyString(), anyList(), anyString(), anyString(), any());
    verify(aliases, never()).listActive(anyList());
    verify(logs)
        .insert(
            eq(9L),
            eq(""),
            eq(""),
            eq("Hi"),
            eq("body"),
            isNull(),
            isNull(),
            eq("skipped"),
            eq("muted"),
            eq("e1"),
            eq(100L),
            eq(7L));
  }

  @Test
  void deliver_mentionedOverridesMute() {
    when(redis.hasKey(RedisKeys.pcActive(9L))).thenReturn(false);
    when(aliases.listActive(List.of(9L))).thenReturn(List.of());
    NotifySendEvent event =
        new NotifySendEvent(
            "e1",
            NotifySendEvent.CHANNEL_PUSH,
            List.of(9L),
            "Hi",
            "body",
            Map.of("messageId", 100L, "dialogId", 7L, "mentioned", true));

    channel.deliver(event);

    verify(mutes, never()).mutedUserIds(anyLong(), anyList());
    verify(aliases).listActive(List.of(9L));
  }

  @Test
  void deliverAfterDelay_skipsWhenRead() {
    when(reads.isRead(100L, 9L)).thenReturn(true);
    when(mutes.mutedUserIds(eq(7L), anyList())).thenReturn(Set.of());
    AppPushDelayQueue.DelayedJob job =
        new AppPushDelayQueue.DelayedJob(
            "e1", List.of(9L), "Hi", "body", Map.of("messageId", 100L, "dialogId", 7L));

    channel.deliverAfterDelay(job);

    verify(aliases, never()).listActive(anyList());
    verify(logs)
        .insert(
            eq(9L),
            anyString(),
            anyString(),
            eq("Hi"),
            eq("body"),
            isNull(),
            isNull(),
            eq("skipped"),
            eq("already_read"),
            eq("e1"),
            eq(100L),
            eq(7L));
  }

  @Test
  void extractLong_aliases() {
    assertEquals(12L, AppPushChannel.extractLong(Map.of("message_id", "12"), "messageId", "message_id"));
    assertEquals(0L, AppPushChannel.extractLong(Map.of(), "messageId"));
    assertTrue(AppPushChannel.eventSilent(Map.of("isSilent", true)));
    assertTrue(AppPushChannel.mentioned(Map.of("mentioned", "1")));
  }
}
