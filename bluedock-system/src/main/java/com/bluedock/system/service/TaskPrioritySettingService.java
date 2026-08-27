package com.bluedock.system.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 任务优先级：`POST /api/system/priority`，存 `bluedock_settings.name=priority`。 */
@Service
public class TaskPrioritySettingService {
  public static final String SETTING_NAME = "priority";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public TaskPrioritySettingService(
      SettingRepository settings,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  /** 全员可读（建任务选优先级）。 */
  public List<Map<String, Object>> get() {
    List<Map<String, Object>> n = normalize(loadRaw());
    return n.isEmpty() ? defaults() : n;
  }

  public List<Map<String, Object>> save(Object listRaw) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    if (!(listRaw instanceof List<?> list) || list.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    List<Map<String, Object>> normalized = normalize(list);
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(normalized));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    return normalized;
  }

  private List<?> loadRaw() {
    return settings
        .findSettingJson(SETTING_NAME)
        .map(
            json -> {
              try {
                return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
              } catch (Exception e) {
                return List.of();
              }
            })
        .orElse(List.of());
  }

  private List<Map<String, Object>> defaults() {
    return normalize(
        List.of(
            Map.of(
                "name", "重要且紧急",
                "color", "#ED4014",
                "days", 1,
                "priority", 1,
                "isDefault", 1),
            Map.of(
                "name", "重要不紧急",
                "color", "#F16B62",
                "days", 3,
                "priority", 2,
                "isDefault", 0),
            Map.of(
                "name", "紧急不重要",
                "color", "#19C919",
                "days", 5,
                "priority", 3,
                "isDefault", 0),
            Map.of(
                "name", "不重要不紧急",
                "color", "#2D8CF0",
                "days", 0,
                "priority", 4,
                "isDefault", 0)));
  }

  static List<Map<String, Object>> normalize(List<?> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    Integer defaultIndex = null;
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> raw)) {
        continue;
      }
      String name = str(raw, "name").trim();
      String color = str(raw, "color").trim();
      int priority = intVal(raw, "priority", 0);
      if (name.isEmpty() || color.isEmpty() || priority <= 0) {
        continue;
      }
      int days = intVal(raw, "days", 0);
      boolean isDefault = flag(raw, "isDefault", "is_default");
      if (defaultIndex == null && isDefault) {
        defaultIndex = out.size();
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", name);
      row.put("color", color);
      row.put("days", days);
      row.put("priority", priority);
      row.put("isDefault", isDefault ? 1 : 0);
      out.add(row);
    }
    if (!out.isEmpty()) {
      int def = defaultIndex == null ? 0 : defaultIndex;
      for (int i = 0; i < out.size(); i++) {
        out.get(i).put("isDefault", i == def ? 1 : 0);
      }
    }
    return out;
  }

  private static String str(Map<?, ?> m, String key) {
    Object v = m.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private static int intVal(Map<?, ?> m, String key, int def) {
    Object v = m.get(key);
    if (v == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static boolean flag(Map<?, ?> m, String camel, String snake) {
    Object v = m.get(camel);
    if (v == null) {
      v = m.get(snake);
    }
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    String s = String.valueOf(v).trim();
    return "1".equals(s) || "true".equalsIgnoreCase(s) || "open".equalsIgnoreCase(s);
  }
}
