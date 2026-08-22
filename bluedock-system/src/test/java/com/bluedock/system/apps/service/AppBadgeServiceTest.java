package com.bluedock.system.apps.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.system.apps.domain.InstalledApp;
import com.bluedock.system.apps.repo.AppBadgeRepository;
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
class AppBadgeServiceTest {
  @Mock AppBadgeRepository badges;
  @Mock InstalledAppService installedApps;
  @Mock RealtimeFanoutPublisher fanout;

  AppBadgeService service;

  @BeforeEach
  void setUp() {
    service = new AppBadgeService(badges, installedApps, fanout);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void set_requiresSecret() {
    assertThrows(BusinessException.class, () -> service.set("okr", "", 1L, "", 1, false));
  }

  @Test
  void set_upsertsAndFanout() {
    when(installedApps.secretOf("okr")).thenReturn(Optional.of("sec"));
    InstalledApp app = new InstalledApp();
    app.setAppId("okr");
    app.setMenus(List.of(Map.of("key", "home")));
    when(installedApps.findInstalled("okr")).thenReturn(Optional.of(app));

    Map<String, Object> out = service.set("okr", "sec", List.of(7L, 8L), "home", 3, true);
    assertEquals(2, out.get("affected"));
    verify(badges).upsert("okr", "home", 7L, 3, true);
    verify(badges).upsert("okr", "home", 8L, 3, true);
    ArgumentCaptor<RealtimeFanoutEvent> cap = ArgumentCaptor.forClass(RealtimeFanoutEvent.class);
    verify(fanout).publish(cap.capture());
    assertEquals(RealtimeEventTypes.APP_BADGE, cap.getValue().type());
  }

  @Test
  void clear_deletesCurrentUser() {
    AuthContext.set(new AuthUser(9L));
    when(installedApps.isInstalled("okr")).thenReturn(true);
    when(installedApps.customAppIds()).thenReturn(java.util.Set.of());
    InstalledApp app = new InstalledApp();
    app.setMenus(List.of(Map.of("key", "")));
    when(installedApps.findInstalled("okr")).thenReturn(Optional.of(app));

    service.clear("okr", "");
    verify(badges).delete(eq("okr"), eq(""), eq(9L));
    verify(fanout).publish(any());
  }
}
