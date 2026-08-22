package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AttendanceSettingService {
  public static final String SETTING_NAME = "attendanceSetting";
  public static final String SECRET_MASK = "********";
  public static final String MODE_LOCATION = "locat";
  public static final String MODE_FACE = "face";
  private static final Set<String> SECRET_FIELDS = Set.of("mapKey", "reportKey");
  private static final int RADIUS_MIN = 50;
  private static final int RADIUS_MAX = 5000;
  private static final int RADIUS_DEFAULT = 500;

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public AttendanceSettingService(
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
    return maskSecrets(load());
  }

  /** 非管理员只读（成员端判断功能是否开启）。 */
  public Map<String, Object> loadPublic() {
    return load();
  }

  public Map<String, Object> save(Map<String, Object> body) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    Map<String, Object> merged = mergeIncoming(load(), body);
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(merged));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_CONFIG);
    }
    return maskSecrets(merged);
  }

  private Map<String, Object> load() {
    Map<String, Object> out = defaults();
    settings
        .findSettingJson(SETTING_NAME)
        .ifPresent(
            json -> {
              try {
                Map<String, Object> parsed =
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                out.putAll(parsed);
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  static Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("open", "close");
    m.put("time", List.of("09:00", "18:00"));
    m.put("advance", 60);
    m.put("delay", 60);
    m.put("remindIn", 5);
    m.put("remindExceed", 10);
    m.put("modes", List.of("manual", "auto"));
    m.put("edit", "open");
    m.put("faceUpload", "open");
    m.put("reportKey", "");
    m.put("installCmd", "");
    m.put("mapProvider", "");
    m.put("mapKey", "");
    m.put("locationLatitude", 0);
    m.put("locationLongitude", 0);
    m.put("locationRadius", RADIUS_DEFAULT);
    return m;
  }

  static Map<String, Object> mergeIncoming(Map<String, Object> current, Map<String, Object> body) {
    Map<String, Object> out = new LinkedHashMap<>(defaults());
    if (current != null) {
      out.putAll(current);
    }
    Map<String, Object> oldSecrets = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : out.entrySet()) {
      if (SECRET_FIELDS.contains(e.getKey())) {
        oldSecrets.put(e.getKey(), e.getValue());
      }
    }
    if (body != null) {
      out.putAll(body);
    }
    for (Map.Entry<String, Object> e : oldSecrets.entrySet()) {
      if (isMaskedOrBlank(out.get(e.getKey()))) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  static Map<String, Object> maskSecrets(Map<String, Object> raw) {
    Map<String, Object> m = new LinkedHashMap<>(raw == null ? Map.of() : raw);
    for (Map.Entry<String, Object> e : m.entrySet()) {
      if (SECRET_FIELDS.contains(e.getKey()) && !MeetingSettingService.str(m, e.getKey()).isBlank()) {
        e.setValue(SECRET_MASK);
      }
    }
    return m;
  }

  static boolean isMaskedOrBlank(Object v) {
    String s = v == null ? "" : String.valueOf(v).trim();
    return s.isBlank() || SECRET_MASK.equals(s) || s.contains("****");
  }

  public boolean isOpen(Map<String, Object> cfg) {
    return MeetingSettingService.openFlag(cfg, "open", false);
  }

  public List<String> modes(Map<String, Object> cfg) {
    Object v = cfg.get("modes");
    if (v instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object o : list) {
        if (o != null) {
          out.add(String.valueOf(o).trim().toLowerCase(Locale.ROOT));
        }
      }
      return out;
    }
    if (v instanceof String s && !s.isBlank()) {
      List<String> out = new ArrayList<>();
      for (String part : s.split("[,|]")) {
        if (!part.isBlank()) {
          out.add(part.trim().toLowerCase(Locale.ROOT));
        }
      }
      return out;
    }
    return List.of("manual");
  }

  public boolean modeEnabled(Map<String, Object> cfg, String mode) {
    return modes(cfg).contains(mode.toLowerCase(Locale.ROOT));
  }

  public String[] workTime(Map<String, Object> cfg) {
    Object v = cfg.get("time");
    if (v instanceof List<?> list && list.size() >= 2) {
      return new String[] {String.valueOf(list.get(0)), String.valueOf(list.get(1))};
    }
    return new String[] {"09:00", "18:00"};
  }

  public int advance(Map<String, Object> cfg) {
    return MeetingSettingService.intVal(cfg, "advance", 60);
  }

  public int delay(Map<String, Object> cfg) {
    return MeetingSettingService.intVal(cfg, "delay", 60);
  }

  public String reportKey(Map<String, Object> cfg) {
    return MeetingSettingService.str(cfg, "reportKey");
  }

  public boolean editAllowed(Map<String, Object> cfg) {
    return MeetingSettingService.openFlag(cfg, "edit", true);
  }

  /** 是否允许成员自行上传人脸。 */
  public boolean faceUploadAllowed(Map<String, Object> cfg) {
    return MeetingSettingService.openFlag(cfg, "faceUpload", true);
  }

  /** 上班前打卡提醒分钟数；0 关闭。 */
  public int remindIn(Map<String, Object> cfg) {
    return MeetingSettingService.intVal(cfg, "remindIn", 5);
  }

  /** 上班后缺卡提醒分钟数；0 关闭。 */
  public int remindExceed(Map<String, Object> cfg) {
    return MeetingSettingService.intVal(cfg, "remindExceed", 10);
  }

  /** 允许签到中心纬度；未配置为 0。 */
  public double locationLatitude(Map<String, Object> cfg) {
    return doubleVal(cfg, "locationLatitude", 0);
  }

  /** 允许签到中心经度；未配置为 0。 */
  public double locationLongitude(Map<String, Object> cfg) {
    return doubleVal(cfg, "locationLongitude", 0);
  }

  /** 定位签到半径（米），钳制 50–5000。 */
  public int locationRadius(Map<String, Object> cfg) {
    int r = MeetingSettingService.intVal(cfg, "locationRadius", RADIUS_DEFAULT);
    if (r < RADIUS_MIN) {
      return RADIUS_MIN;
    }
    if (r > RADIUS_MAX) {
      return RADIUS_MAX;
    }
    return r;
  }

  public boolean locationConfigured(Map<String, Object> cfg) {
    double lat = locationLatitude(cfg);
    double lng = locationLongitude(cfg);
    return Double.compare(lat, 0) != 0 || Double.compare(lng, 0) != 0;
  }

  static double doubleVal(Map<String, Object> cfg, String key, double def) {
    if (cfg == null) {
      return def;
    }
    Object v = cfg.get(key);
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(v).trim());
    } catch (Exception e) {
      return def;
    }
  }
}
