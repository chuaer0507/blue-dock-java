package com.bluedock.common.notify.mail;

import tools.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 从 emailSetting JSON Map 解析 SMTP / 忽略地址 / 未读汇总规则。 */
public final class EmailSettingMaps {
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");
  private static final ObjectMapper JSON = new ObjectMapper();

  private EmailSettingMaps() {}

  public static SmtpMailClient.SmtpConfig toSmtp(Map<String, Object> cfg) {
    if (cfg == null) {
      cfg = Map.of();
    }
    int port = 465;
    try {
      String p = str(cfg, "smtpPort");
      if (!p.isBlank()) {
        port = Integer.parseInt(p);
      }
    } catch (NumberFormatException ignored) {
      // keep default
    }
    boolean ssl = open(str(cfg, "smtpSsl"), true);
    return new SmtpMailClient.SmtpConfig(
        str(cfg, "smtpHost"),
        port,
        str(cfg, "smtpUsername"),
        str(cfg, "smtpPassword"),
        ssl,
        str(cfg, "fromAlias"),
        str(cfg, "fromAddress"));
  }

  public static List<String> parseIgnore(Map<String, Object> cfg) {
    List<String> out = new ArrayList<>();
    if (cfg == null) {
      return out;
    }
    Object raw = cfg.get("ignoreAddr");
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        if (o != null) {
          String e = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
          if (!e.isEmpty()) {
            out.add(e);
          }
        }
      }
      return out;
    }
    if (raw != null) {
      for (String part : String.valueOf(raw).split("[,;\\s]+")) {
        String e = part.trim().toLowerCase(Locale.ROOT);
        if (!e.isEmpty()) {
          out.add(e);
        }
      }
    }
    return out;
  }

  /** {@code noticeMessage=open} 时允许未读汇总。 */
  public static boolean noticeMessageOpen(Map<String, Object> cfg) {
    return open(str(cfg, "noticeMessage"), false);
  }

  public static int unreadMinute(Map<String, Object> cfg, boolean userDialog) {
    String key = userDialog ? "messageUnreadUserMinute" : "messageUnreadGroupMinute";
    Object v = cfg == null ? null : cfg.get(key);
    if (v == null) {
      return userDialog ? 30 : 60;
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return userDialog ? 30 : 60;
    }
  }

  /**
   * 解析允许发送时段：{@code [["00:00","09:00"],...]} 或 JSON 字符串；空则永不匹配。
   */
  public static List<LocalTime[]> parseTimeRanges(Map<String, Object> cfg) {
    List<LocalTime[]> out = new ArrayList<>();
    if (cfg == null) {
      return out;
    }
    Object raw = cfg.get("messageUnreadTimeRanges");
    if (raw instanceof String s) {
      String t = s.trim();
      if (t.isEmpty()) {
        return out;
      }
      if (!(t.startsWith("[") || t.startsWith("{"))) {
        return out;
      }
      try {
        raw = JSON.readValue(t, Object.class);
      } catch (Exception e) {
        return out;
      }
    }
    if (!(raw instanceof List<?> outer)) {
      return out;
    }
    // 兼容单段 ["00:00","09:00"]
    if (!outer.isEmpty() && !(outer.get(0) instanceof List) && outer.size() >= 2) {
      LocalTime[] one = toRange(outer.get(0), outer.get(1));
      if (one != null) {
        out.add(one);
      }
      return out;
    }
    for (Object item : outer) {
      if (item instanceof List<?> pair && pair.size() >= 2) {
        LocalTime[] r = toRange(pair.get(0), pair.get(1));
        if (r != null) {
          out.add(r);
        }
      }
    }
    return out;
  }

  /** 当前时刻（服务器本地）是否落在任一允许时段。 */
  public static boolean isTimeInRanges(List<LocalTime[]> ranges, LocalTime now) {
    if (ranges == null || ranges.isEmpty() || now == null) {
      return false;
    }
    int cur = now.toSecondOfDay();
    for (LocalTime[] r : ranges) {
      if (r == null || r.length < 2 || r[0] == null || r[1] == null) {
        continue;
      }
      int start = r[0].toSecondOfDay();
      int end = r[1].toSecondOfDay();
      if (start <= cur && cur <= end) {
        return true;
      }
    }
    return false;
  }

  public static boolean isTimeInRanges(Map<String, Object> cfg) {
    return isTimeInRanges(parseTimeRanges(cfg), LocalTime.now());
  }

  private static LocalTime[] toRange(Object a, Object b) {
    LocalTime start = parseHm(a);
    LocalTime end = parseHm(b);
    if (start == null || end == null) {
      return null;
    }
    return new LocalTime[] {start, end};
  }

  private static LocalTime parseHm(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    if (s.isEmpty()) {
      return null;
    }
    try {
      return LocalTime.parse(s, HM);
    } catch (DateTimeParseException e) {
      try {
        return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"));
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }

  private static boolean open(String v, boolean defaultOpen) {
    if (v == null || v.isBlank()) {
      return defaultOpen;
    }
    String s = v.trim().toLowerCase(Locale.ROOT);
    return "open".equals(s) || "true".equals(s) || "1".equals(s);
  }

  private static String str(Map<String, Object> cfg, String key) {
    Object v = cfg.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }
}
