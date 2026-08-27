package com.bluedock.system.apps.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.apps.catalog.OfficialAppCatalog;
import com.bluedock.system.apps.domain.InstalledApp;
import com.bluedock.system.apps.repo.AppBadgeRepository;
import com.bluedock.system.apps.repo.InstalledAppRepository;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstalledAppService {
  private final InstalledAppRepository apps;
  private final AppBadgeRepository badges;
  private final SettingRepository settings;
  private final AdminGuard adminGuard;
  private final MicroAppMenuService microAppMenus;
  private final AppLifecycleHookClient lifecycleHooks;
  private final ObjectMapper objectMapper;

  public InstalledAppService(
      InstalledAppRepository apps,
      AppBadgeRepository badges,
      SettingRepository settings,
      AdminGuard adminGuard,
      MicroAppMenuService microAppMenus,
      AppLifecycleHookClient lifecycleHooks,
      ObjectMapper objectMapper) {
    this.apps = apps;
    this.badges = badges;
    this.settings = settings;
    this.adminGuard = adminGuard;
    this.microAppMenus = microAppMenus;
    this.lifecycleHooks = lifecycleHooks;
    this.objectMapper = objectMapper;
  }

  public boolean isInstalled(String appId) {
    if ("appstore".equals(appId)) {
      return true;
    }
    return apps.findInstalled(appId).isPresent() || customAppIds().contains(appId);
  }

  public Optional<InstalledApp> findInstalled(String appId) {
    if ("appstore".equals(appId)) {
      InstalledApp a = new InstalledApp();
      a.setAppId("appstore");
      a.setName("AppStore");
      a.setSecret("");
      a.setStatus("installed");
      a.setVersion("1.0.0");
      a.setMenus(List.of(Map.of("key", "", "visibleTo", List.of("admin"))));
      return Optional.of(a);
    }
    return apps.findInstalled(appId);
  }

  public Optional<String> secretOf(String appId) {
    return apps.findInstalled(appId).map(InstalledApp::getSecret).filter(s -> s != null && !s.isBlank());
  }

  public List<Map<String, Object>> catalog() {
    adminGuard.requireAdmin();
    Set<String> installed = new HashSet<>();
    for (InstalledApp a : apps.listInstalled()) {
      installed.add(a.getAppId());
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : OfficialAppCatalog.list()) {
      Map<String, Object> copy = new LinkedHashMap<>(row);
      copy.put("installed", installed.contains(String.valueOf(row.get("id"))));
      out.add(copy);
    }
    return out;
  }

  public List<Map<String, Object>> listForAdmin() {
    adminGuard.requireAdmin();
    List<Map<String, Object>> out = new ArrayList<>();
    out.add(
        Map.of("id", "appstore", "name", "AppStore", "status", "installed", "version", "1.0.0"));
    for (InstalledApp a : apps.listInstalled()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", a.getAppId());
      row.put("name", a.getName());
      row.put("status", a.getStatus());
      row.put("version", a.getVersion() == null || a.getVersion().isBlank() ? "1.0.0" : a.getVersion());
      out.add(row);
    }
    return out;
  }

  @Transactional
  public Map<String, Object> install(
      String appId, String name, String secret, String version, List<Map<String, Object>> menus) {
    adminGuard.requireAdmin();
    String id = trim(appId);
    if (id.isEmpty() || "appstore".equals(id)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    Optional<OfficialAppCatalog.Entry> catalog = OfficialAppCatalog.find(id);
    String resolvedName = trim(name);
    if (resolvedName.isEmpty()) {
      resolvedName = catalog.map(OfficialAppCatalog.Entry::name).orElse(id);
    }
    String resolvedVersion = trim(version);
    if (resolvedVersion.isEmpty()) {
      resolvedVersion = catalog.map(OfficialAppCatalog.Entry::version).orElse("1.0.0");
    }
    String resolvedSecret = trim(secret);
    if (resolvedSecret.isEmpty()) {
      resolvedSecret = UUID.randomUUID().toString().replace("-", "");
    }
    List<Map<String, Object>> resolvedMenus =
        menus == null || menus.isEmpty()
            ? catalog.map(OfficialAppCatalog.Entry::menus).orElse(defaultMenus())
            : menus;

    InstalledApp a = new InstalledApp();
    a.setAppId(id);
    a.setName(resolvedName);
    a.setSecret(resolvedSecret);
    a.setStatus("installed");
    a.setVersion(resolvedVersion);
    a.setMenus(resolvedMenus);
    apps.upsert(a);

    List<Map<String, Object>> menuItems =
        catalog.map(OfficialAppCatalog.Entry::menuItems).orElse(List.of());
    if (!menuItems.isEmpty()) {
      microAppMenus.upsertRegistryEntry(id, resolvedName, resolvedVersion, menuItems);
    }
    boolean hookOk = lifecycleHooks.notify("install", id, resolvedName, resolvedVersion);
    lifecycleHooks.afterMutate(
        hookOk,
        "install",
        id,
        () -> {
          apps.markUninstalled(id);
          microAppMenus.removeRegistryEntry(id);
        });
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("name", resolvedName);
    out.put("status", "installed");
    out.put("version", resolvedVersion);
    return out;
  }

  @Transactional
  public Map<String, Object> update(
      String appId, String name, String secret, String version, List<Map<String, Object>> menus) {
    adminGuard.requireAdmin();
    String id = trim(appId);
    if (id.isEmpty() || "appstore".equals(id)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    InstalledApp existing =
        apps.findInstalled(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.APPS_NOT_INSTALLED));
    Optional<OfficialAppCatalog.Entry> catalog = OfficialAppCatalog.find(id);

    String resolvedName = trim(name);
    if (resolvedName.isEmpty()) {
      resolvedName = existing.getName();
    }
    String resolvedVersion = trim(version);
    if (resolvedVersion.isEmpty()) {
      resolvedVersion =
          catalog
              .map(OfficialAppCatalog.Entry::version)
              .orElse(
                  existing.getVersion() == null || existing.getVersion().isBlank()
                      ? "1.0.0"
                      : existing.getVersion());
    }
    String resolvedSecret = trim(secret);
    if (resolvedSecret.isEmpty()) {
      resolvedSecret = existing.getSecret() == null ? "" : existing.getSecret();
    }
    List<Map<String, Object>> resolvedMenus =
        menus == null || menus.isEmpty() ? existing.getMenus() : menus;
    if (resolvedMenus == null || resolvedMenus.isEmpty()) {
      resolvedMenus = catalog.map(OfficialAppCatalog.Entry::menus).orElse(defaultMenus());
    }

    InstalledApp a = new InstalledApp();
    a.setAppId(id);
    a.setName(resolvedName);
    a.setSecret(resolvedSecret);
    a.setStatus("installed");
    a.setVersion(resolvedVersion);
    a.setMenus(resolvedMenus);
    apps.upsert(a);

    List<Map<String, Object>> menuItems =
        catalog.map(OfficialAppCatalog.Entry::menuItems).orElse(List.of());
    if (!menuItems.isEmpty()) {
      microAppMenus.upsertRegistryEntry(id, resolvedName, resolvedVersion, menuItems);
    }
    boolean hookOk = lifecycleHooks.notify("update", id, resolvedName, resolvedVersion);
    lifecycleHooks.afterMutate(hookOk, "update", id, null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("name", resolvedName);
    out.put("status", "installed");
    out.put("version", resolvedVersion);
    return out;
  }

  @Transactional
  public Map<String, Object> uninstall(String appId) {
    adminGuard.requireAdmin();
    String id = trim(appId);
    if (id.isEmpty() || "appstore".equals(id)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    apps.markUninstalled(id);
    badges.deleteByApp(id);
    microAppMenus.removeRegistryEntry(id);
    // 卸载后 Hook 失败不回滚（避免注册表与侧车不一致时无法卸）
    lifecycleHooks.notify("uninstall", id, "", "");
    return Map.of("id", id, "status", "uninstalled");
  }

  public Set<String> customAppIds() {
    Set<String> ids = new HashSet<>();
    String json = settings.findSettingJson("microAppMenu").orElse("[]");
    try {
      List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
      for (Map<String, Object> app : list) {
        if (app != null && app.get("id") != null) {
          ids.add(String.valueOf(app.get("id")));
        }
      }
    } catch (Exception ignored) {
      // ignore
    }
    return ids;
  }

  private static List<Map<String, Object>> defaultMenus() {
    return List.of(Map.of("key", "", "visibleTo", List.of("all")));
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
