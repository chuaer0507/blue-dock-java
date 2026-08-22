package com.bluedock.system.apps.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.apps.domain.InstalledApp;
import com.bluedock.system.apps.repo.AppBadgeRepository;
import com.bluedock.system.apps.repo.InstalledAppRepository;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstalledAppServiceTest {
  @Mock InstalledAppRepository apps;
  @Mock AppBadgeRepository badges;
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock MicroAppMenuService microAppMenus;
  @Mock AppLifecycleHookClient lifecycleHooks;

  InstalledAppService service;

  @BeforeEach
  void setUp() {
    service =
        new InstalledAppService(
            apps, badges, settings, adminGuard, microAppMenus, lifecycleHooks, new ObjectMapper());
  }

  @Test
  void catalog_marksInstalled() {
    InstalledApp okr = new InstalledApp();
    okr.setAppId("okr");
    when(apps.listInstalled()).thenReturn(List.of(okr));

    List<Map<String, Object>> catalog = service.catalog();
    assertTrue(catalog.stream().anyMatch(r -> "okr".equals(r.get("id")) && Boolean.TRUE.equals(r.get("installed"))));
    assertTrue(
        catalog.stream()
            .anyMatch(r -> "ai".equals(r.get("id")) && Boolean.FALSE.equals(r.get("installed"))));
    verify(adminGuard).requireAdmin();
  }

  @Test
  void install_fromCatalog_fillsAndSyncsMenu() {
    when(lifecycleHooks.notify(eq("install"), eq("approve"), anyString(), anyString()))
        .thenReturn(true);

    Map<String, Object> out = service.install("approve", null, null, null, List.of());
    assertEquals("approve", out.get("id"));
    assertEquals("审批", out.get("name"));
    assertEquals("installed", out.get("status"));
    assertEquals("1.0.0", out.get("version"));

    ArgumentCaptor<InstalledApp> cap = ArgumentCaptor.forClass(InstalledApp.class);
    verify(apps).upsert(cap.capture());
    assertFalse(cap.getValue().getSecret().isBlank());
    verify(microAppMenus)
        .upsertRegistryEntry(eq("approve"), eq("审批"), eq("1.0.0"), any());
    verify(lifecycleHooks).afterMutate(eq(true), eq("install"), eq("approve"), any());
  }

  @Test
  void install_hookFailStrict_rollsBack() {
    when(lifecycleHooks.notify(eq("install"), eq("approve"), anyString(), anyString()))
        .thenReturn(false);
    doAnswer(
            inv -> {
              Runnable rollback = inv.getArgument(3);
              if (rollback != null) {
                rollback.run();
              }
              throw new BusinessException(
                  com.bluedock.common.exception.ErrorCodes.BAD_REQUEST,
                  I18nKeys.APPS_LIFECYCLE_HOOK_FAILED,
                  "install",
                  "approve");
            })
        .when(lifecycleHooks)
        .afterMutate(eq(false), eq("install"), eq("approve"), any());

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.install("approve", null, null, null, List.of()));
    assertEquals(I18nKeys.APPS_LIFECYCLE_HOOK_FAILED, ex.getMessageKey());
    verify(apps).markUninstalled("approve");
    verify(microAppMenus).removeRegistryEntry("approve");
  }

  @Test
  void update_requiresInstalled() {
    when(apps.findInstalled("okr")).thenReturn(Optional.empty());
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.update("okr", null, null, null, null));
    assertEquals(I18nKeys.APPS_NOT_INSTALLED, ex.getMessageKey());
  }

  @Test
  void update_ok() {
    InstalledApp existing = new InstalledApp();
    existing.setAppId("okr");
    existing.setName("OKR");
    existing.setSecret("old");
    existing.setVersion("1.0.0");
    existing.setMenus(List.of(Map.of("key", "home")));
    when(apps.findInstalled("okr")).thenReturn(Optional.of(existing));
    when(lifecycleHooks.notify(eq("update"), eq("okr"), anyString(), anyString())).thenReturn(true);

    Map<String, Object> out = service.update("okr", "OKR Pro", null, "1.1.0", List.of());
    assertEquals("1.1.0", out.get("version"));
    assertEquals("OKR Pro", out.get("name"));
    verify(apps).upsert(any(InstalledApp.class));
    verify(microAppMenus).upsertRegistryEntry(eq("okr"), eq("OKR Pro"), eq("1.1.0"), any());
    verify(lifecycleHooks).afterMutate(eq(true), eq("update"), eq("okr"), isNull());
  }

  @Test
  void uninstall_clearsMenuAndBadges() {
    Map<String, Object> out = service.uninstall("okr");
    assertEquals("uninstalled", out.get("status"));
    verify(apps).markUninstalled("okr");
    verify(badges).deleteByApp("okr");
    verify(microAppMenus).removeRegistryEntry("okr");
    verify(lifecycleHooks).notify("uninstall", "okr", "", "");
  }

  @Test
  void uninstall_appstoreRejected() {
    assertThrows(BusinessException.class, () -> service.uninstall("appstore"));
    verify(apps, never()).markUninstalled(any());
  }
}
