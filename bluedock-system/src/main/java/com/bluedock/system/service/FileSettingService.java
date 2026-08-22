package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 文件相关系统设置：`api/system/setting/file`。 */
@Service
public class FileSettingService {
  public static final String SETTING_NAME = "fileSetting";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public FileSettingService(
      SettingRepository settings,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  public Map<String, Object> get() {
    adminGuard.requireAdmin();
    return load();
  }

  public Map<String, Object> save(Map<String, Object> body) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    Map<String, Object> current = load();
    if (body != null) {
      current.putAll(body);
    }
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(current));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    return current;
  }

  /** 上传上限字节数；空配置默认 1G。无需管理员。 */
  public long uploadMaxBytes() {
    Object raw = load().get("uploadMaxMb");
    if (raw == null) {
      return 1024L * 1024L * 1024L;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return 1024L * 1024L * 1024L;
    }
    try {
      long mb = Long.parseLong(s);
      if (mb <= 0) {
        return 1024L * 1024L * 1024L;
      }
      return mb * 1024L * 1024L;
    } catch (NumberFormatException ex) {
      return 1024L * 1024L * 1024L;
    }
  }

  private Map<String, Object> load() {
    Map<String, Object> out = defaults();
    settings
        .findSettingJson(SETTING_NAME)
        .ifPresent(
            json -> {
              try {
                out.putAll(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  private Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    // 空=默认 1G（与 overview 一致，单位 MB）
    m.put("uploadMaxMb", "");
    m.put("packPermission", "all");
    m.put("packUserIds", "");
    m.put("imageOptimize", "close");
    m.put("saveInternetImage", "close");
    m.put("videoTranscode", "close");
    return m;
  }
}
