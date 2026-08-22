package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegPolicyImplTest {
  @Mock SystemGeneralSettingService general;
  @Mock EmailSettingService email;

  RegPolicyImpl policy;

  @BeforeEach
  void setUp() {
    policy = new RegPolicyImpl(general, email);
  }

  @Test
  void needInvite_whenRegInvite() {
    when(general.loadRaw()).thenReturn(Map.of("reg", "invite"));
    assertTrue(policy.needInvite());
    when(general.loadRaw()).thenReturn(Map.of("reg", "open"));
    assertFalse(policy.needInvite());
  }

  @Test
  void regVerify_open() {
    when(email.loadRaw()).thenReturn(Map.of("regVerify", "open"));
    assertTrue(policy.isRegVerifyOpen());
    when(email.loadRaw()).thenReturn(Map.of("regVerify", "close"));
    assertFalse(policy.isRegVerifyOpen());
  }
}
