package com.bluedock.user.favorite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.user.favorite.repo.UserFavoriteRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserFavoriteServiceTest {
  @Mock UserFavoriteRepository favorites;
  @Mock JdbcTemplate jdbc;
  UserFavoriteService service;

  @BeforeEach
  void setUp() {
    service = new UserFavoriteService(favorites, jdbc);
    AuthContext.set(new AuthUser(4L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void toggle_addThenRemove() {
    when(jdbc.queryForObject(
            eq("SELECT COUNT(1) FROM bluedock_projects WHERE id = ? AND deleted_at IS NULL"),
            eq(Integer.class),
            eq(11L)))
        .thenReturn(1);
    when(favorites.findId(4L, "project", 11L)).thenReturn(Optional.empty());
    Map<String, Object> added = service.toggle("project", 11L);
    assertTrue((Boolean) added.get("favorited"));
    verify(favorites).insert(4L, "project", 11L);

    when(favorites.findId(4L, "project", 11L)).thenReturn(Optional.of(99L));
    Map<String, Object> removed = service.toggle("project", 11L);
    assertFalse((Boolean) removed.get("favorited"));
    verify(favorites).delete(4L, "project", 11L);
  }

  @Test
  void check_falseWhenMissing() {
    when(favorites.findId(4L, "task", 1L)).thenReturn(Optional.empty());
    assertEquals(false, service.check("task", 1L).get("favorited"));
  }
}
