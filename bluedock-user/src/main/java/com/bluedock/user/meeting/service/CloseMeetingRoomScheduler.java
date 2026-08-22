package com.bluedock.user.meeting.service;

import com.bluedock.common.meeting.MeetingInviteBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.user.meeting.agora.AgoraChannelClient;
import com.bluedock.user.meeting.config.MeetingRuntimeConfig;
import com.bluedock.user.meeting.domain.Meeting;
import com.bluedock.user.meeting.repo.MeetingRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 自动关房：空闲频道写 end_at，并同步会议卡片。 */
@Component
public class CloseMeetingRoomScheduler {
  private static final Logger log = LoggerFactory.getLogger(CloseMeetingRoomScheduler.class);

  private final MeetingRepository meetings;
  private final MeetingRuntimeConfig props;
  private final AgoraChannelClient agora;
  private final StringRedisTemplate redis;
  private final ObjectProvider<MeetingInviteBridge> inviteBridge;

  public CloseMeetingRoomScheduler(
      MeetingRepository meetings,
      MeetingRuntimeConfig props,
      AgoraChannelClient agora,
      StringRedisTemplate redis,
      ObjectProvider<MeetingInviteBridge> inviteBridge) {
    this.meetings = meetings;
    this.props = props;
    this.agora = agora;
    this.redis = redis;
    this.inviteBridge = inviteBridge;
  }

  @Scheduled(fixedDelayString = "${bluedock.meeting.close-check-ms:60000}")
  public void tick() {
    if (!props.isEnabled()) {
      return;
    }
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.meetingCloseTick(), "1", Duration.ofMinutes(10));
    if (Boolean.FALSE.equals(first)) {
      return;
    }

    boolean hasRest =
        props.getAppId() != null
            && !props.getAppId().isBlank()
            && props.getApiKey() != null
            && !props.getApiKey().isBlank()
            && props.getApiSecret() != null
            && !props.getApiSecret().isBlank();
    if (!hasRest && !props.isAllowCloseWithoutRest()) {
      return;
    }

    int idle = Math.max(props.getCloseIdleMinutes(), 1);
    List<Meeting> stale = meetings.listStaleOpen(LocalDateTime.now().minusMinutes(idle), 100);
    for (Meeting m : stale) {
      boolean empty;
      if (hasRest) {
        empty =
            agora.isChannelEmpty(
                props.getAppId(), props.getApiKey(), props.getApiSecret(), m.getChannel());
      } else {
        empty = true;
      }
      if (!empty) {
        meetings.touch(m.getMeetingId());
        continue;
      }
      LocalDateTime endAt = LocalDateTime.now();
      meetings.markEnded(m.getMeetingId(), endAt);
      m.setEndAt(endAt);
      MeetingInviteBridge bridge = inviteBridge.getIfAvailable();
      if (bridge != null) {
        bridge.markMeetingEnded(m.getMeetingId(), toPayload(m));
      }
      log.info("meeting closed meetingId={}", m.getMeetingId());
    }
  }

  private static Map<String, Object> toPayload(Meeting m) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", m.getId());
    data.put("meetingId", m.getMeetingId());
    data.put("name", m.getName());
    data.put("channel", m.getChannel());
    data.put("userId", m.getUserId());
    data.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
    data.put("endAt", m.getEndAt() == null ? null : m.getEndAt().toString());
    return data;
  }
}
