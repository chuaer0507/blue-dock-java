package com.bluedock.common.notify.apppush;

import java.util.Locale;
import java.util.Map;

/** 从 appPushSetting JSON Map 解析 APP 推送凭证。 */
public final class AppPushSettingMaps {
  private AppPushSettingMaps() {}

  public static boolean enabled(Map<String, Object> cfg) {
    return open(str(cfg, "open"), false) || open(str(cfg, "enabled"), false);
  }

  public static String iosKey(Map<String, Object> cfg) {
    return str(cfg, "iosKey");
  }

  public static String iosSecret(Map<String, Object> cfg) {
    return str(cfg, "iosSecret");
  }

  public static String androidKey(Map<String, Object> cfg) {
    return str(cfg, "androidKey");
  }

  public static String androidSecret(Map<String, Object> cfg) {
    return str(cfg, "androidSecret");
  }

  public static String aliasType(Map<String, Object> cfg) {
    String t = str(cfg, "aliasType");
    return t.isBlank() ? "bluedock" : t;
  }

  public static boolean production(Map<String, Object> cfg) {
    String v = str(cfg, "productionMode");
    if (v.isBlank()) {
      return true;
    }
    return open(v, true);
  }

  private static boolean open(String v, boolean defaultOpen) {
    if (v == null || v.isBlank()) {
      return defaultOpen;
    }
    String s = v.trim().toLowerCase(Locale.ROOT);
    return "open".equals(s) || "true".equals(s) || "1".equals(s);
  }

  private static String str(Map<String, Object> cfg, String key) {
    if (cfg == null) {
      return "";
    }
    Object v = cfg.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }
}
