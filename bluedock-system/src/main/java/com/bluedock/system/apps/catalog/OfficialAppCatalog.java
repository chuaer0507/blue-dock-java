package com.bluedock.system.apps.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内置官方插件目录（无外部商店 / Docker）。仅供注册表安装补全元数据。
 */
public final class OfficialAppCatalog {
  private OfficialAppCatalog() {}

  public record Entry(
      String id,
      String name,
      String description,
      String version,
      List<Map<String, Object>> menus,
      List<Map<String, Object>> menuItems) {}

  private static final List<Entry> ENTRIES =
      List.of(
          entry("ai", "AI 助手", "AI assistant plugin", badge("home"), menu("AI 助手", "/apps/ai")),
          entry("approve", "审批", "Approval workflow plugin", badge("home"), menu("审批", "/apps/approve")),
          entry(
              "attendance",
              "签到",
              "Attendance plugin",
              badge("home"),
              menu("签到", "/apps/attendance")),
          entry("face", "人脸", "Face recognition plugin", badge("home"), menu("人脸", "/apps/face")),
          entry("office", "OnlyOffice", "Office preview plugin", badge(""), menu("Office", "/apps/office")),
          entry("drawio", "流程图", "Draw.io plugin", badge(""), menu("流程图", "/apps/drawio")),
          entry("minder", "脑图", "Mind map plugin", badge(""), menu("脑图", "/apps/minder")),
          entry("okr", "OKR", "OKR plugin", badge("home"), menu("OKR", "/apps/okr")),
          entry("search", "搜索引擎", "Search engine plugin", badge(""), menu("搜索", "/apps/search")),
          entry(
              "fileview",
              "文件预览",
              "File preview plugin",
              badge(""),
              menu("文件预览", "/apps/fileview")));

  public static List<Map<String, Object>> list() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Entry e : ENTRIES) {
      out.add(toMap(e));
    }
    return out;
  }

  public static Optional<Entry> find(String appId) {
    if (appId == null || appId.isBlank()) {
      return Optional.empty();
    }
    String id = appId.trim();
    return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst();
  }

  private static Map<String, Object> toMap(Entry e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.id());
    m.put("name", e.name());
    m.put("description", e.description());
    m.put("version", e.version());
    m.put("menus", e.menus());
    m.put("menuItems", e.menuItems());
    return m;
  }

  private static Entry entry(
      String id,
      String name,
      String description,
      List<Map<String, Object>> menus,
      List<Map<String, Object>> menuItems) {
    return new Entry(id, name, description, "1.0.0", menus, menuItems);
  }

  private static List<Map<String, Object>> badge(String key) {
    return List.of(Map.of("key", key, "visibleTo", List.of("all")));
  }

  private static List<Map<String, Object>> menu(String label, String url) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("location", "application");
    item.put("label", label);
    item.put("icon", "");
    item.put("url", url);
    item.put("type", "iframe");
    item.put("keepAlive", true);
    item.put("key", "");
    return List.of(item);
  }
}
