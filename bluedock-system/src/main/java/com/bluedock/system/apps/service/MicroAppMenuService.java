package com.bluedock.system.apps.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MicroAppMenuService {
  private final SettingRepository settings;
  private final AdminGuard adminGuard;
  private final ObjectMapper objectMapper;

  public MicroAppMenuService(
      SettingRepository settings, AdminGuard adminGuard, ObjectMapper objectMapper) {
    this.settings = settings;
    this.adminGuard = adminGuard;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> get() {
    long userId = AuthContext.requireUserId();
    boolean admin = adminGuard.isAdmin(userId);
    List<Map<String, Object>> stored = loadStored();
    List<Map<String, Object>> filtered = filterForUser(stored, admin, userId);
    return formatForResponse(filtered, admin);
  }

  @Transactional
  public List<Map<String, Object>> save(Object list) {
    adminGuard.requireAdmin();
    List<Map<String, Object>> normalized = normalizeList(list);
    persist(normalized);
    return formatForResponse(normalized, true);
  }

  /** 安装注册表写入后合并到 microAppMenu（管理员已在上层校验）。 */
  @Transactional
  public void upsertRegistryEntry(
      String appId, String name, String version, List<Map<String, Object>> menuItems) {
    if (appId == null || appId.isBlank() || menuItems == null || menuItems.isEmpty()) {
      return;
    }
    List<Map<String, Object>> stored = new ArrayList<>(loadStored());
    stored.removeIf(a -> appId.equals(String.valueOf(a.get("id"))));
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("id", appId);
    raw.put("name", name == null ? appId : name);
    raw.put("version", version == null || version.isBlank() ? "1.0.0" : version);
    raw.put("menuItems", menuItems);
    raw.put("visibleTo", List.of("all"));
    Map<String, Object> normalized = normalizeApp(raw);
    if (normalized == null) {
      return;
    }
    stored.add(normalized);
    persist(stored);
  }

  /** 卸载时从 microAppMenu 移除对应入口。 */
  @Transactional
  public void removeRegistryEntry(String appId) {
    if (appId == null || appId.isBlank()) {
      return;
    }
    List<Map<String, Object>> stored = new ArrayList<>(loadStored());
    boolean removed = stored.removeIf(a -> appId.equals(String.valueOf(a.get("id"))));
    if (removed) {
      persist(stored);
    }
  }

  private void persist(List<Map<String, Object>> normalized) {
    try {
      settings.upsert("microAppMenu", objectMapper.writeValueAsString(normalized));
    } catch (Exception e) {
      settings.upsert("microAppMenu", "[]");
    }
  }

  private List<Map<String, Object>> loadStored() {
    String json = settings.findSettingJson("microAppMenu").orElse("[]");
    try {
      List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
      return list == null ? List.of() : list;
    } catch (Exception e) {
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> normalizeList(Object list) {
    if (!(list instanceof List<?> raw)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : raw) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      Map<String, Object> app = normalizeApp((Map<String, Object>) m);
      if (app != null) {
        out.add(app);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> normalizeApp(Map<String, Object> item) {
    String id = str(item.get("id")).trim();
    if (id.isEmpty()) {
      return null;
    }
    String name = str(item.get("name")).trim();
    String version = str(item.get("version")).trim();
    if (version.isEmpty()) {
      version = "custom";
    }
    List<?> menuItems;
    if (item.get("menuItems") instanceof List<?> mi) {
      menuItems = mi;
    } else if (item.get("menu") instanceof Map<?, ?>) {
      menuItems = List.of(item.get("menu"));
    } else {
      return null;
    }
    List<Map<String, Object>> menus = new ArrayList<>();
    for (Object menu : menuItems) {
      if (menu instanceof Map<?, ?> mm) {
        Map<String, Object> n = normalizeMenu((Map<String, Object>) mm, name.isEmpty() ? id : name);
        if (n != null) {
          menus.add(n);
        }
      }
    }
    if (menus.isEmpty()) {
      return null;
    }
    Map<String, Object> app = new LinkedHashMap<>();
    app.put("id", id);
    app.put("name", name);
    app.put("version", version);
    app.put("menuItems", menus);
    app.put("visibleTo", normalizeVisible(item.get("visibleTo")));
    return app;
  }

  private Map<String, Object> normalizeMenu(Map<String, Object> menu, String fallbackLabel) {
    String url = str(menu.get("url")).trim();
    if (url.isEmpty()) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("location", blankDefault(str(menu.get("location")), "application"));
    out.put("label", blankDefault(str(menu.get("label")), fallbackLabel));
    out.put("icon", str(menu.get("icon")).trim());
    out.put("url", url);
    out.put("type", blankDefault(str(menu.get("type")).toLowerCase(), "iframe"));
    out.put("keepAlive", bool(menu.get("keepAlive"), true));
    out.put("disableScopeCss", bool(menu.get("disableScopeCss"), false));
    out.put("autoDarkTheme", bool(menu.get("autoDarkTheme"), true));
    out.put("transparent", bool(menu.get("transparent"), false));
    out.put("key", str(menu.get("key")).trim());
    out.put("badgeClearOnOpen", bool(menu.get("badgeClearOnOpen"), false));
    Object vis = menu.get("visibleTo");
    if (vis != null) {
      out.put("visibleTo", normalizeVisible(vis));
    }
    return out;
  }

  private List<Map<String, Object>> filterForUser(
      List<Map<String, Object>> apps, boolean admin, long userId) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> app : apps) {
      List<String> visible = normalizeVisible(app.get("visibleTo"));
      if (!visibleTo(visible, admin, userId)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> menus =
          app.get("menuItems") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
      List<Map<String, Object>> kept = new ArrayList<>();
      for (Map<String, Object> menu : menus) {
        if (menu == null) {
          continue;
        }
        if (!menu.containsKey("visibleTo") || visibleTo(normalizeVisible(menu.get("visibleTo")), admin, userId)) {
          kept.add(menu);
        }
      }
      if (kept.isEmpty()) {
        continue;
      }
      Map<String, Object> copy = new LinkedHashMap<>(app);
      copy.put("menuItems", kept);
      out.add(copy);
    }
    return out;
  }

  private List<Map<String, Object>> formatForResponse(List<Map<String, Object>> apps, boolean keepVisible) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> app : apps) {
      Map<String, Object> copy = new LinkedHashMap<>(app);
      if (keepVisible) {
        copy.put("visibleTo", normalizeVisible(copy.get("visibleTo")));
      } else {
        copy.remove("visibleTo");
      }
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> menus =
          copy.get("menuItems") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
      List<Map<String, Object>> formatted = new ArrayList<>();
      for (Map<String, Object> menu : menus) {
        Map<String, Object> m = new LinkedHashMap<>(menu);
        m.put("keepAlive", bool(m.get("keepAlive"), true));
        m.put("disableScopeCss", bool(m.get("disableScopeCss"), false));
        m.put("autoDarkTheme", bool(m.get("autoDarkTheme"), true));
        m.put("transparent", bool(m.get("transparent"), false));
        m.put("key", str(m.get("key")));
        m.put("badgeClearOnOpen", bool(m.get("badgeClearOnOpen"), false));
        m.remove("visibleTo");
        formatted.add(m);
      }
      copy.put("menuItems", formatted);
      out.add(copy);
    }
    return out;
  }

  private static List<String> normalizeVisible(Object value) {
    List<String> list = new ArrayList<>();
    if (value instanceof List<?> raw) {
      for (Object o : raw) {
        String s = str(o).trim();
        if (!s.isEmpty()) {
          list.add(s);
        }
      }
    } else if (value != null) {
      for (String part : str(value).split(",")) {
        String s = part.trim();
        if (!s.isEmpty()) {
          list.add(s);
        }
      }
    }
    if (list.isEmpty()) {
      return List.of("admin");
    }
    if (list.contains("all")) {
      return List.of("all");
    }
    return List.copyOf(list);
  }

  private static boolean visibleTo(List<String> visible, boolean admin, long userId) {
    if (visible.contains("all")) {
      return true;
    }
    if (admin && visible.contains("admin")) {
      return true;
    }
    return userId > 0 && visible.contains(Long.toString(userId));
  }

  private static boolean bool(Object v, boolean def) {
    if (v == null) {
      return def;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    String s = str(v).toLowerCase();
    if ("true".equals(s) || "1".equals(s)) {
      return true;
    }
    if ("false".equals(s) || "0".equals(s)) {
      return false;
    }
    return def;
  }

  private static String blankDefault(String s, String def) {
    return s == null || s.isBlank() ? def : s.trim();
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}
