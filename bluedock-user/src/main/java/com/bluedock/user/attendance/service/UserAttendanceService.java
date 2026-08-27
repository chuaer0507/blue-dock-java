package com.bluedock.user.attendance.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.attendance.AttendanceFaceBridge;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.service.AttendanceSettingService;
import com.bluedock.user.attendance.repo.UserAttendanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAttendanceService {
  private static final Pattern MAC =
      Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final int MAX_MAC = 3;

  private final UserAttendanceRepository attendances;
  private final AttendanceSettingService settings;
  private final ObjectMapper json;
  private final ObjectProvider<AttendanceFaceBridge> faceBridge;

  public UserAttendanceService(
      UserAttendanceRepository attendances,
      AttendanceSettingService settings,
      ObjectMapper json,
      ObjectProvider<AttendanceFaceBridge> faceBridge) {
    this.attendances = attendances;
    this.settings = settings;
    this.json = json;
    this.faceBridge = faceBridge;
  }

  public Map<String, Object> get() {
    long me = AuthContext.requireUserId();
    Map<String, Object> cfg = settings.loadPublic();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("open", settings.isOpen(cfg) ? "open" : "close");
    out.put("modes", settings.modes(cfg));
    out.put("time", java.util.Arrays.asList(settings.workTime(cfg)));
    out.put("edit", settings.editAllowed(cfg) ? "open" : "close");
    out.put("faceUpload", settings.faceUploadAllowed(cfg) ? "open" : "close");
    out.put("hasFace", attendances.hasFace(me));
    out.put("facePlugin", facePluginAvailable());
    out.put("macAddresses", attendances.listMacAddresses(me));
    out.put("locationLatitude", settings.locationLatitude(cfg));
    out.put("locationLongitude", settings.locationLongitude(cfg));
    out.put("locationRadius", settings.locationRadius(cfg));
    LocalDate today = LocalDate.now();
    out.put("today", attendances.findRecord(me, today).orElse(null));
    return out;
  }

  @Transactional
  public Map<String, Object> save(
      String macAddressesRaw,
      Integer punch,
      Double latitude,
      Double longitude,
      Long faceUploadObjectId,
      Long faceCaptureObjectId) {
    long me = AuthContext.requireUserId();
    Map<String, Object> cfg = settings.loadPublic();
    requireOpen(cfg);
    if (macAddressesRaw != null) {
      if (!settings.editAllowed(cfg)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MODE_DENIED);
      }
      List<String> macAddresses = parseMacAddresses(macAddressesRaw);
      attendances.replaceMacAddresses(me, macAddresses);
    }
    if (faceUploadObjectId != null && faceUploadObjectId > 0) {
      enrollFace(me, cfg, faceUploadObjectId);
    }
    if (faceCaptureObjectId != null && faceCaptureObjectId > 0) {
      punchFace(me, cfg, faceCaptureObjectId);
    } else if (latitude != null && longitude != null) {
      punchLocation(me, cfg, latitude, longitude);
    } else if (punch != null && punch != 0) {
      punch(me, cfg, "manual", null, null);
    }
    return get();
  }

  public Map<String, Object> list(String yearMonth) {
    long me = AuthContext.requireUserId();
    Map<String, Object> cfg = settings.loadPublic();
    requireOpen(cfg);
    LocalDate from;
    LocalDate to;
    if (yearMonth != null && yearMonth.matches("\\d{4}-\\d{2}")) {
      from = LocalDate.parse(yearMonth + "-01");
      to = from.withDayOfMonth(from.lengthOfMonth());
    } else {
      LocalDate today = LocalDate.now();
      from = today.withDayOfMonth(1);
      to = today.withDayOfMonth(today.lengthOfMonth());
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("from", from.toString());
    out.put("to", to.toString());
    out.put("list", attendances.listRecords(me, from, to));
    return out;
  }

  /** WiFi 路由器上报 MAC；公开接口。 */
  @Transactional
  public Map<String, Object> report(String macAddressRaw, String key) {
    Map<String, Object> cfg = settings.loadPublic();
    requireOpen(cfg);
    if (!settings.modeEnabled(cfg, "auto")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MODE_DENIED);
    }
    String expect = settings.reportKey(cfg);
    if (expect.isEmpty() || key == null || !expect.equals(key.trim())) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.ATTENDANCE_REPORT_DENIED);
    }
    String macAddress = normalizeMacAddress(macAddressRaw);
    long userId =
        attendances
            .findUserIdByMacAddress(macAddress)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.ATTENDANCE_MAC_INVALID));
    punch(userId, cfg, "auto", null, null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("userId", userId);
    out.put("macAddress", macAddress);
    out.put("ok", true);
    return out;
  }

  /** 人脸设备刷脸打卡；公开接口（reportKey + userId + 抓拍对象）。 */
  @Transactional
  public Map<String, Object> reportFace(long userId, long faceCaptureObjectId, String key) {
    Map<String, Object> cfg = settings.loadPublic();
    requireOpen(cfg);
    String expect = settings.reportKey(cfg);
    if (expect.isEmpty() || key == null || !expect.equals(key.trim())) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.ATTENDANCE_REPORT_DENIED);
    }
    if (userId <= 0 || faceCaptureObjectId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_INVALID);
    }
    punchFace(userId, cfg, faceCaptureObjectId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("userId", userId);
    out.put("ok", true);
    return out;
  }

  public Map<String, Object> installHint() {
    Map<String, Object> cfg = settings.loadPublic();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("installCmd", String.valueOf(cfg.getOrDefault("installCmd", "")));
    out.put("open", settings.isOpen(cfg) ? "open" : "close");
    return out;
  }

  private void enrollFace(long userId, Map<String, Object> cfg, long uploadObjectId) {
    requireFacePlugin();
    if (!settings.faceUploadAllowed(cfg)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_DENIED);
    }
    if (!attendances.uploadObjectExists(uploadObjectId)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_INVALID);
    }
    attendances.upsertFace(userId, uploadObjectId);
  }

  private void punchFace(long userId, Map<String, Object> cfg, long captureUploadObjectId) {
    AttendanceFaceBridge plugin = requireFacePlugin();
    if (!settings.modeEnabled(cfg, AttendanceSettingService.MODE_FACE)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MODE_DENIED);
    }
    long enrolled =
        attendances
            .findFaceUploadObjectId(userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_MISSING));
    if (!attendances.uploadObjectExists(captureUploadObjectId)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_INVALID);
    }
    if (!plugin.match(userId, enrolled, captureUploadObjectId)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_MISMATCH);
    }
    punch(userId, cfg, AttendanceSettingService.MODE_FACE, null, null);
  }

  private AttendanceFaceBridge requireFacePlugin() {
    AttendanceFaceBridge plugin = faceBridge.getIfAvailable();
    if (plugin == null || !plugin.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_FACE_PLUGIN_MISSING);
    }
    return plugin;
  }

  private boolean facePluginAvailable() {
    AttendanceFaceBridge plugin = faceBridge.getIfAvailable();
    return plugin != null && plugin.available();
  }

  private void punchLocation(long userId, Map<String, Object> cfg, double latitude, double longitude) {
    if (!settings.modeEnabled(cfg, AttendanceSettingService.MODE_LOCATION)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MODE_DENIED);
    }
    if (!settings.locationConfigured(cfg)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_LOCATION_CONFIG);
    }
    if (!validCoordinate(latitude, longitude)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_LOCATION_INVALID);
    }
    double centerLat = settings.locationLatitude(cfg);
    double centerLng = settings.locationLongitude(cfg);
    int radius = settings.locationRadius(cfg);
    double meters = distanceMeters(centerLat, centerLng, latitude, longitude);
    if (meters > radius) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_LOCATION_OUTSIDE);
    }
    punch(userId, cfg, AttendanceSettingService.MODE_LOCATION, latitude, longitude);
  }

  private void punch(
      long userId, Map<String, Object> cfg, String mode, Double latitude, Double longitude) {
    if (!settings.modeEnabled(cfg, mode)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MODE_DENIED);
    }
    LocalDateTime now = LocalDateTime.now();
    if (!withinWindow(cfg, now.toLocalTime())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_OUTSIDE);
    }
    String[] wt = settings.workTime(cfg);
    LocalTime start = parseHm(wt[0]);
    String section = now.toLocalTime().isBefore(start.plusHours(4)) ? "in" : "out";
    LocalDate day = now.toLocalDate();
    List<Map<String, Object>> times = readTimes(userId, day);
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("at", now.toLocalTime().format(HMS));
    item.put("mode", mode);
    item.put("section", section);
    if (latitude != null && longitude != null) {
      item.put("latitude", latitude);
      item.put("longitude", longitude);
    }
    times.add(item);
    try {
      attendances.upsertRecord(userId, day, json.writeValueAsString(times));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_CONFIG);
    }
  }

  private boolean withinWindow(Map<String, Object> cfg, LocalTime now) {
    String[] wt = settings.workTime(cfg);
    LocalTime start = parseHm(wt[0]);
    LocalTime end = parseHm(wt[1]);
    int advance = settings.advance(cfg);
    int delay = settings.delay(cfg);
    LocalTime earliest = start.minusMinutes(advance);
    LocalTime latest = end.plusMinutes(delay);
    return !now.isBefore(earliest) && !now.isAfter(latest);
  }

  private List<Map<String, Object>> readTimes(long userId, LocalDate day) {
    return attendances
        .findRecord(userId, day)
        .map(
            row -> {
              try {
                return json.readValue(
                    String.valueOf(row.get("times")),
                    new TypeReference<List<Map<String, Object>>>() {});
              } catch (Exception e) {
                return new ArrayList<Map<String, Object>>();
              }
            })
        .orElseGet(ArrayList::new);
  }

  private void requireOpen(Map<String, Object> cfg) {
    if (!settings.isOpen(cfg)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_DISABLED);
    }
  }

  private static List<String> parseMacAddresses(String raw) {
    Set<String> set = new LinkedHashSet<>();
    for (String part : raw.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      set.add(normalizeMacAddress(part));
    }
    if (set.size() > MAX_MAC) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MAC_LIMIT);
    }
    return new ArrayList<>(set);
  }

  private static String normalizeMacAddress(String raw) {
    if (raw == null || raw.isBlank() || !MAC.matcher(raw.trim()).matches()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_MAC_INVALID);
    }
    return raw.trim().toUpperCase(Locale.ROOT).replace('-', ':');
  }

  private static LocalTime parseHm(String raw) {
    try {
      String s = raw == null ? "09:00" : raw.trim();
      if (s.length() == 5) {
        return LocalTime.parse(s, HM);
      }
      return LocalTime.parse(s, HMS);
    } catch (Exception e) {
      return LocalTime.of(9, 0);
    }
  }

  static boolean validCoordinate(double latitude, double longitude) {
    return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
  }

  /** Haversine 球面距离（米）。 */
  static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double earth = 6_371_000d;
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * earth * Math.asin(Math.min(1.0, Math.sqrt(a)));
  }
}
