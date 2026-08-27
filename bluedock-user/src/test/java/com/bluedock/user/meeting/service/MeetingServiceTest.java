package com.bluedock.user.meeting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.meeting.MeetingInviteBridge;
import com.bluedock.system.service.MeetingSettingService;
import com.bluedock.user.meeting.config.MeetingProperties;
import com.bluedock.user.meeting.config.MeetingRuntimeConfig;
import com.bluedock.user.meeting.domain.Meeting;
import com.bluedock.user.meeting.repo.MeetingRepository;
import com.bluedock.user.meeting.web.dto.MeetingOpenView;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {
  @Mock MeetingRepository meetings;
  @Mock UserAccountRepository users;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock ObjectProvider<MeetingInviteBridge> inviteBridge;
  @Mock MeetingSettingService meetingSettings;

  MeetingProperties props = new MeetingProperties();
  MeetingService service;

  @BeforeEach
  void setUp() {
    props.setEnabled(true);
    props.setAllowDevToken(true);
    props.setAppId("");
    props.setAppCertificate("");
    props.setChannelSalt("salt");
    lenient().when(meetingSettings.loadRaw()).thenReturn(Map.of());
    lenient().when(inviteBridge.getIfAvailable()).thenReturn(null);
    MeetingRuntimeConfig cfg = new MeetingRuntimeConfig(props, meetingSettings);
    service = new MeetingService(meetings, users, redis, cfg, new ObjectMapper(), inviteBridge);
  }

  private void stubRedis() {
    when(redis.opsForValue()).thenReturn(valueOps);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void create_requires_login() {
    assertThrows(
        BusinessException.class,
        () -> service.open("create", null, "Standup", null, null, null, null));
  }

  @Test
  void create_ok() {
    stubRedis();
    AuthContext.set(new AuthUser(1L));
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setNickname("Alice");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    doNothing().when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));

    MeetingOpenView view = service.open("create", null, "Standup", null, null, null, null);
    assertTrue(view.meetingId() != null && view.meetingId().length() == 11);
    assertTrue(view.token().startsWith("dev."));
    verify(meetings).insert(any(Meeting.class));
    assertEquals("Standup", view.name());
  }
}
