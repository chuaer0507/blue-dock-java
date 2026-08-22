package com.bluedock.user.meeting.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.meeting.MeetingInviteBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.service.MeetingSettingService;
import com.bluedock.user.meeting.agora.AgoraChannelClient;
import com.bluedock.user.meeting.config.MeetingProperties;
import com.bluedock.user.meeting.config.MeetingRuntimeConfig;
import com.bluedock.user.meeting.domain.Meeting;
import com.bluedock.user.meeting.repo.MeetingRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CloseMeetingRoomSchedulerTest {
  @Mock MeetingRepository meetings;
  @Mock AgoraChannelClient agora;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock ObjectProvider<MeetingInviteBridge> inviteBridge;
  @Mock MeetingInviteBridge bridge;
  @Mock MeetingSettingService meetingSettings;

  MeetingProperties props = new MeetingProperties();
  CloseMeetingRoomScheduler scheduler;

  @BeforeEach
  void setUp() {
    props.setEnabled(true);
    props.setAllowCloseWithoutRest(true);
    props.setCloseIdleMinutes(10);
    props.setAppId("");
    props.setApiKey("");
    props.setApiSecret("");
    lenient().when(meetingSettings.loadRaw()).thenReturn(Map.of());
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(eq(RedisKeys.meetingCloseTick()), eq("1"), any(Duration.class)))
        .thenReturn(true);
    when(inviteBridge.getIfAvailable()).thenReturn(bridge);
    MeetingRuntimeConfig cfg = new MeetingRuntimeConfig(props, meetingSettings);
    scheduler = new CloseMeetingRoomScheduler(meetings, cfg, agora, redis, inviteBridge);
  }

  @Test
  void closesStaleWithoutRest() {
    Meeting m = new Meeting();
    m.setId(1L);
    m.setMeetingId("ABC");
    m.setChannel("ch");
    m.setName("N");
    m.setUserId(1L);
    m.setCreatedAt(LocalDateTime.now().minusHours(1));
    when(meetings.listStaleOpen(any(LocalDateTime.class), anyInt())).thenReturn(List.of(m));

    scheduler.tick();

    verify(meetings).markEnded(eq("ABC"), any(LocalDateTime.class));
    verify(bridge).markMeetingEnded(eq("ABC"), any());
    verify(agora, never()).isChannelEmpty(any(), any(), any(), any());
  }
}
