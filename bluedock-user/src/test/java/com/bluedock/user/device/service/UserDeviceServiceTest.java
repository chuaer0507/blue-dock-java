package com.bluedock.user.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.auth.service.TokenService;
import com.bluedock.user.device.repo.UserDeviceRepository;
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
class UserDeviceServiceTest {
  @Mock UserDeviceRepository devices;
  @Mock TokenService tokens;
  UserDeviceService service;

  @BeforeEach
  void setUp() {
    service = new UserDeviceService(devices, tokens, new ObjectMapper(), 3600);
    AuthContext.set(new AuthUser(5L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void onLogin_insertsDevice() {
    service.onLogin(5L, "tok123", "Electron/BlueDock", "1.2.3.4");
    verify(devices).insert(eq(5L), anyString(), anyString(), any());
    verify(devices).pruneOldest(5L, UserDeviceService.DEVICE_LIMIT);
  }

  @Test
  void list_marksCurrent() {
    String token = "abc";
    String hash = TokenService.hashOf(token);
    when(devices.listActive(5L, UserDeviceService.DEVICE_LIMIT))
        .thenReturn(List.of(Map.of("id", 1L, "userId", 5L, "hash", hash, "detail", "{}")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) service.list(token).get("list");
    assertEquals(1, list.get(0).get("isCurrent"));
  }

  @Test
  void logout_revokesToken() {
    when(devices.findActive(5L, 9L))
        .thenReturn(Optional.of(Map.of("id", 9L, "hash", "deadbeef", "userId", 5L)));
    service.logout(9L);
    verify(devices).softDelete(9L);
    verify(tokens).revokeByHash("deadbeef");
  }
}
