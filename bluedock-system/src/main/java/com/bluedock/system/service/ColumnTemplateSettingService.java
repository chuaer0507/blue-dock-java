package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 创建项目列模板：`POST /api/system/column/template`，存 `bluedock_settings.name=columnTemplate`。 */
@Service
public class ColumnTemplateSettingService {
  public static final String SETTING_NAME = "columnTemplate";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public ColumnTemplateSettingService(
      SettingRepository settings,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  /** 全员可读（新建项目选模板）。 */
  public List<Map<String, Object>> get() {
    List<Map<String, Object>> loaded = load();
    return loaded.isEmpty() ? defaults() : loaded;
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

  private List<Map<String, Object>> load() {
    return settings
        .findSettingJson(SETTING_NAME)
        .map(
            json -> {
              try {
                List<Object> raw =
                    objectMapper.readValue(json, new TypeReference<List<Object>>() {});
                return normalize(raw);
              } catch (Exception e) {
                return List.<Map<String, Object>>of();
              }
            })
        .orElse(List.of());
  }

  private static List<Map<String, Object>> defaults() {
    return List.of(
        row("软件开发", List.of("产品规划", "前端开发", "后端开发", "测试", "发布", "其他")),
        row("产品开发", List.of("产品计划", "正在设计", "正在研发", "测试", "准备发布", "发布成功")));
  }

  static List<Map<String, Object>> normalize(List<?> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> raw)) {
        continue;
      }
      Object nameObj = raw.get("name");
      String name = nameObj == null ? "" : String.valueOf(nameObj).trim();
      List<String> columns = parseColumns(raw.get("columns"));
      if (name.isEmpty() || columns.isEmpty()) {
        continue;
      }
      out.add(row(name, columns));
    }
    return out;
  }

  private static Map<String, Object> row(String name, List<String> columns) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("columns", columns);
    return m;
  }

  private static List<String> parseColumns(Object v) {
    Set<String> uniq = new LinkedHashSet<>();
    if (v instanceof List<?> list) {
      for (Object o : list) {
        if (o == null) {
          continue;
        }
        String s = String.valueOf(o).trim();
        if (!s.isEmpty()) {
          uniq.add(s);
        }
      }
    } else if (v instanceof String s && !s.isBlank()) {
      for (String part : s.split(",")) {
        String t = part.trim();
        if (!t.isEmpty()) {
          uniq.add(t);
        }
      }
    }
    return new ArrayList<>(uniq);
  }
}
