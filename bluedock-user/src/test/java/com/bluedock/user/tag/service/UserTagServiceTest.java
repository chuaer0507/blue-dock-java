package com.bluedock.user.tag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.tag.domain.UserTag;
import com.bluedock.user.tag.repo.UserTagRepository;
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

@ExtendWith(MockitoExtension.class)
class UserTagServiceTest {
  @Mock UserTagRepository tags;
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  UserTagService service;

  @BeforeEach
  void setUp() {
    service = new UserTagService(tags, users, adminGuard);
    AuthContext.set(new AuthUser(3L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void add_self_inserts() {
    UserAccount me = new UserAccount();
    me.setUserId(3L);
    me.setIsBot(0);
    when(users.findByUserId(3L)).thenReturn(Optional.of(me));
    when(tags.countByUser(3L)).thenReturn(0);
    when(tags.findByUserAndName(3L, "靠谱")).thenReturn(Optional.empty());

    var view = service.add(null, " 靠谱 ");
    assertEquals("靠谱", view.name());
    assertEquals(3L, view.userId());
    assertEquals(3L, view.creatorUserId());
    ArgumentCaptor<UserTag> cap = ArgumentCaptor.forClass(UserTag.class);
    verify(tags).insert(cap.capture());
    assertEquals("靠谱", cap.getValue().getName());
  }

  @Test
  void add_rejectsDuplicate() {
    UserAccount me = new UserAccount();
    me.setUserId(3L);
    me.setIsBot(0);
    when(users.findByUserId(3L)).thenReturn(Optional.of(me));
    when(tags.countByUser(3L)).thenReturn(1);
    when(tags.findByUserAndName(3L, "靠谱")).thenReturn(Optional.of(new UserTag()));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.add(3L, "靠谱"));
    assertEquals(I18nKeys.USER_TAG_EXISTS, ex.getMessageKey());
    verify(tags, never()).insert(any());
  }

  @Test
  void update_deniedForNonCreator() {
    UserTag t = tag(9L, 8L, 1L, "旧");
    when(tags.findActive(9L)).thenReturn(Optional.of(t));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.update(9L, "新"));
    assertEquals(I18nKeys.USER_TAG_DENIED, ex.getMessageKey());
  }

  @Test
  void delete_allowsAdmin() {
    UserTag t = tag(9L, 8L, 1L, "旧");
    when(tags.findActive(9L)).thenReturn(Optional.of(t));
    when(adminGuard.isAdmin(3L)).thenReturn(true);

    Map<String, Object> out = service.delete(9L);
    assertEquals(true, out.get("deleted"));
    verify(tags).softDelete(9L);
  }

  @Test
  void recognize_toggles() {
    UserTag t = tag(9L, 8L, 1L, "靠谱");
    when(tags.findActive(9L)).thenReturn(Optional.of(t));
    when(tags.hasRecognition(9L, 3L)).thenReturn(false);
    when(tags.countRecognitions(9L)).thenReturn(1L);

    Map<String, Object> out = service.recognize(9L);
    assertEquals(true, out.get("recognized"));
    verify(tags).insertRecognition(9L, 3L);
  }

  @Test
  void lists_defaultsToSelf() {
    when(users.existsByUserId(3L)).thenReturn(true);
    UserTag t = tag(9L, 3L, 3L, "靠谱");
    when(tags.listByUser(3L)).thenReturn(List.of(t));
    when(tags.countRecognitions(9L)).thenReturn(2L);
    when(tags.hasRecognition(9L, 3L)).thenReturn(true);

    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) service.lists(null).get("list");
    assertEquals(1, list.size());
    assertEquals(3L, service.lists(null).get("userId"));
  }

  private static UserTag tag(long id, long userId, long creatorId, String name) {
    UserTag t = new UserTag();
    t.setId(id);
    t.setUserId(userId);
    t.setCreatorUserId(creatorId);
    t.setName(name);
    return t;
  }
}
