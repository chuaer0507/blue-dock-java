package com.bluedock.user.app.sort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.user.app.sort.repo.UserAppSortRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAppSortServiceTest {
  @Mock UserAppSortRepository sorts;
  UserAppSortService service;

  @BeforeEach
  void setUp() {
    service = new UserAppSortService(sorts, new ObjectMapper());
    AuthContext.set(new AuthUser(3L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void get_defaultsEmptyGroups() {
    when(sorts.findSortsJson(3L)).thenReturn(Optional.empty());
    @SuppressWarnings("unchecked")
    Map<String, List<String>> out = (Map<String, List<String>>) service.get().get("sorts");
    assertEquals(List.of(), out.get("base"));
    assertEquals(List.of(), out.get("admin"));
  }

  @Test
  void save_normalizesUnique() {
    Map<String, Object> body =
        Map.of("base", List.of("micro:calendar", "micro:calendar", "system:file"), "admin", List.of());
    @SuppressWarnings("unchecked")
    Map<String, List<String>> out = (Map<String, List<String>>) service.save(body).get("sorts");
    assertEquals(List.of("micro:calendar", "system:file"), out.get("base"));
    verify(sorts).upsert(eq(3L), anyString());
  }
}
