package com.bluedock.user.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.mail.SmtpMailClient.SmtpConfig;
import com.bluedock.system.service.EmailSettingService;
import com.bluedock.user.email.repo.UserEmailVerificationRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserEmailServiceTest {
  @Mock UserEmailVerificationRepository verifications;
  @Mock UserAccountRepository users;
  @Mock EmailSettingService emailSettings;

  UserEmailService service;

  @BeforeEach
  void setUp() {
    service = new UserEmailService(verifications, users, emailSettings, "http://localhost:8080");
    AuthContext.set(new AuthUser(7L));
    when(emailSettings.smtpConfig())
        .thenReturn(new SmtpConfig("", 0, "", "", false, "", ""));
    when(verifications.findRecentPending(anyLong(), any())).thenReturn(Optional.empty());
    when(verifications.insert(anyLong(), anyString(), anyString(), anyString())).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  void send_returnsDevCodeWhenSmtpOff() {
    UserAccount u = new UserAccount();
    u.setUserId(7L);
    u.setEmail("a@b.com");
    u.setNickname("A");
    u.setEmailVerify(0);
    when(users.findByUserId(7L)).thenReturn(Optional.of(u));

    Map<String, Object> out = service.send();
    assertTrue((Boolean) out.get("sent"));
    assertTrue(out.containsKey("devCode"));
    verify(verifications).insert(eq(7L), eq("a@b.com"), anyString(), eq("reg"));
  }

  @Test
  void verify_reg_setsEmailVerify() {
    when(verifications.findByCode("c".repeat(64)))
        .thenReturn(
            Optional.of(
                Map.of(
                    "id",
                    1L,
                    "userId",
                    7L,
                    "code",
                    "c".repeat(64),
                    "email",
                    "a@b.com",
                    "type",
                    "reg",
                    "status",
                    0,
                    "createdAt",
                    LocalDateTime.now())));

    Map<String, Object> out = service.verify("c".repeat(64));
    assertEquals(true, out.get("ok"));
    verify(users).updateEmailVerify(7L, 1);
  }

  @Test
  void verify_usedRejected() {
    when(verifications.findByCode("x".repeat(64)))
        .thenReturn(
            Optional.of(
                Map.of(
                    "id",
                    1L,
                    "userId",
                    7L,
                    "code",
                    "x".repeat(64),
                    "email",
                    "a@b.com",
                    "type",
                    "reg",
                    "status",
                    1,
                    "createdAt",
                    LocalDateTime.now())));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.verify("x".repeat(64)));
    assertEquals(I18nKeys.USER_EMAIL_CODE_USED, ex.getMessageKey());
  }
}
