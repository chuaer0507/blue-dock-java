package com.bluedock.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.notify.SystemMsgDmBridge;
import com.bluedock.common.user.UserDisableHandoverBridge;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.web.dto.UserAdminView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceTest {
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  @Mock WirePasswordResolver passwords;
  @Mock PasswordEncoder passwordEncoder;
  @Mock ObjectProvider<LicenseCapacity> licenseCapacity;
  @Mock LicenseCapacity capacity;
  @Mock UserDisableHandoverBridge handoverBridge;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock NotifySendPublisher notifySend;
  @Mock ObjectProvider<SystemMsgDmBridge> systemMsgProvider;
  @Mock SystemMsgDmBridge systemMsg;

  UserAdminService service;

  @BeforeEach
  void setUp() {
    doNothing().when(adminGuard).requireAdmin();
    when(licenseCapacity.getIfAvailable()).thenReturn(capacity);
    doNothing().when(capacity).assertCanAddUser();
    when(notifyPublisher.getIfAvailable()).thenReturn(notifySend);
    when(systemMsgProvider.getIfAvailable()).thenReturn(systemMsg);
    service =
        new UserAdminService(
            users,
            adminGuard,
            passwords,
            passwordEncoder,
            licenseCapacity,
            List.of(handoverBridge),
            notifyPublisher,
            systemMsgProvider);
    AuthContext.set(new AuthUser(10L));
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  void lists_ok() {
    UserAccount u = new UserAccount();
    u.setUserId(9L);
    u.setEmail("a@b.com");
    u.setNickname("A");
    when(users.countForAdmin("a", false)).thenReturn(1);
    when(users.listForAdmin("a", false, 20, 0)).thenReturn(List.of(u));

    Map<String, Object> out = service.lists("a", 1, 20, 0);
    assertEquals(1, out.get("total"));
    assertEquals(1, ((List<?>) out.get("list")).size());
    verify(adminGuard).requireAdmin();
  }

  @Test
  void createUser_ok() {
    when(users.existsByEmail("new@bluedock.local")).thenReturn(false);
    when(passwords.requirePlain("k1", "cipher")).thenReturn("Pass123!");
    when(passwordEncoder.encode("Pass123!")).thenReturn("hash");

    UserPublicView view =
        service.createUser("new@bluedock.local", "新人", "cipher", "k1", "工程师", null);
    assertEquals("new@bluedock.local", view.email());
    assertEquals("新人", view.nickname());
    ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).insert(cap.capture());
    assertEquals("hash", cap.getValue().getPassword());
    assertEquals("[]", cap.getValue().getIdentity());
  }

  @Test
  void createUser_emailTaken() {
    when(users.existsByEmail("a@b.com")).thenReturn(true);
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.createUser("a@b.com", "新人", "c", "k", null, null));
    assertEquals(I18nKeys.USER_EMAIL_TAKEN, ex.getMessageKey());
  }

  @Test
  void normalizeIdentity_stripsSystem() {
    assertEquals("[\"admin\"]", UserAdminService.normalizeIdentity("admin,system,bot"));
    assertEquals("[]", UserAdminService.normalizeIdentity("system"));
    assertTrue(UserAdminService.normalizeIdentity("[\"ldap\"]").contains("ldap"));
  }

  @Test
  void operation_setAdmin_ok() {
    UserAccount u = member(20L, "[]");
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));

    UserAdminView view = service.operation("setAdmin", 20L);
    assertTrue(view.identity().contains("admin"));
    verify(users).updateIdentity(20L, "[\"admin\"]");
  }

  @Test
  void operation_setAdmin_deniedWhenTemporary() {
    UserAccount u = member(20L, "[\"temporary\"]");
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("setAdmin", 20L));
    assertEquals(I18nKeys.USER_OP_TEMPORARY_ADMIN, ex.getMessageKey());
    verify(users, never()).updateIdentity(anyLong(), anyString());
  }

  @Test
  void operation_clearAdmin_selfDenied() {
    UserAccount u = member(10L, "[\"admin\"]");
    when(users.findByUserId(10L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("clearAdmin", 10L));
    assertEquals(I18nKeys.USER_OP_SELF_DENIED, ex.getMessageKey());
  }

  @Test
  void operation_disable_requiresHandover() {
    UserAccount u = member(20L, "[\"admin\"]");
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("disable", 20L));
    assertEquals(I18nKeys.USER_OP_HANDOVER_REQUIRED, ex.getMessageKey());
    verify(handoverBridge, never()).handover(anyLong(), anyLong());
  }

  @Test
  void operation_disable_setsFlagAndAt() {
    UserAccount u = member(20L, "[\"admin\"]");
    UserAccount handover = member(30L, "[]");
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));
    when(users.findByUserId(30L)).thenReturn(Optional.of(handover));

    UserAdminView view = service.operation("disable", 20L, 30L);
    assertTrue(view.identity().contains("disable"));
    assertTrue(!view.identity().contains("admin"));
    verify(handoverBridge).handover(20L, 30L);
    verify(users).updateIdentity(eq(20L), eq("[\"disable\"]"));
    verify(users).updateDisableAt(eq(20L), any());
    ArgumentCaptor<NotifySendEvent> notifyCap = ArgumentCaptor.forClass(NotifySendEvent.class);
    verify(notifySend).publish(notifyCap.capture());
    assertEquals(NotifySendEvent.CHANNEL_DESKTOP, notifyCap.getValue().channel());
    assertEquals(List.of(30L), notifyCap.getValue().userIds());
    verify(systemMsg).sendDm(eq(30L), anyString());
  }

  @Test
  void operation_disable_rejectsDisabledHandover() {
    UserAccount u = member(20L, "[]");
    UserAccount handover = member(30L, "[\"disable\"]");
    handover.setDisableAt(java.time.LocalDateTime.now());
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));
    when(users.findByUserId(30L)).thenReturn(Optional.of(handover));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("disable", 20L, 30L));
    assertEquals(I18nKeys.USER_OP_HANDOVER_INVALID, ex.getMessageKey());
  }

  @Test
  void operation_enable_clears() {
    UserAccount u = member(20L, "[\"disable\"]");
    u.setDisableAt(java.time.LocalDateTime.now());
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));

    UserAdminView view = service.operation("enable", 20L);
    assertEquals("[]", view.identity());
    assertNull(view.disableAt());
    verify(users).updateDisableAt(eq(20L), isNull());
  }

  @Test
  void operation_systemDenied() {
    UserAccount u = member(1L, "[\"system\",\"admin\"]");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("disable", 1L, 30L));
    assertEquals(I18nKeys.USER_OP_SYSTEM_DENIED, ex.getMessageKey());
  }

  @Test
  void operation_botDenied() {
    UserAccount u = member(20L, "[]");
    u.setIsBot(1);
    when(users.findByUserId(20L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.operation("setTemporary", 20L));
    assertEquals(I18nKeys.USER_OP_BOT_DENIED, ex.getMessageKey());
  }

  private static UserAccount member(long id, String identity) {
    UserAccount u = new UserAccount();
    u.setUserId(id);
    u.setEmail("u" + id + "@bluedock.local");
    u.setNickname("U" + id);
    u.setIdentity(identity);
    u.setIsBot(0);
    return u;
  }
}
