package com.bluedock.system.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.export.ExportRunPublisher;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 管理员签到导出：校验后投递 Kafka {@code bluedock.export.run}（kind=attendance），Worker 生成 CSV。
 *
 * <p>下载：{@code GET /api/system/attendance/download?key=}（与任务导出共用 Redis 票）。
 */
@Service
public class AttendanceExportService {
  private static final int MAX_USERS = 100;
  private static final int MAX_DAYS = 35;
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");

  private final AdminGuard adminGuard;
  private final AttendanceSettingService attendanceSettings;
  private final ExportRunPublisher exportPublisher;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public AttendanceExportService(
      AdminGuard adminGuard,
      AttendanceSettingService attendanceSettings,
      ExportRunPublisher exportPublisher,
      ObjectProvider<NotifySendPublisher> notifyPublisher,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.adminGuard = adminGuard;
    this.attendanceSettings = attendanceSettings;
    this.exportPublisher = exportPublisher;
    this.notifyPublisher = notifyPublisher;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  /**
   * @param userIdRaw 逗号分隔用户 id
   * @param dateRaw 日期起止，如 {@code 2026-01-01,2026-01-31}
   * @param timeRaw 班次时间，如 {@code 09:00,18:00}
   */
  public Map<String, Object> export(String userIdRaw, String dateRaw, String timeRaw) {
    adminGuard.requireAdmin();
    Map<String, Object> cfg = attendanceSettings.loadPublic();
    if (!attendanceSettings.isOpen(cfg)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ATTENDANCE_DISABLED);
    }
    List<Long> userIds = parseUserIds(userIdRaw);
    if (userIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
    }
    if (userIds.size() > MAX_USERS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_USER_LIMIT, MAX_USERS);
    }
    String[] dates = parseDateRange(dateRaw);
    String shift = parseShift(timeRaw);
    long me = AuthContext.requireUserId();
    String eventId = UUID.randomUUID().toString().replace("-", "");
    exportPublisher.publish(
        new ExportRunEvent(
            eventId,
            ExportRunEvent.KIND_ATTENDANCE,
            me,
            userIds,
            dates[0],
            dates[1],
            shift));
    notifyProgress(me, "正在导出签到数据，请稍等...");
    return Map.of("accepted", true, "eventId", eventId);
  }

  public ResponseEntity<InputStreamResource> download(String key) throws IOException {
    long me = AuthContext.requireUserId();
    if (key == null || key.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_KEY_INVALID);
    }
    String raw = redis.opsForValue().get(RedisKeys.exportDown(key.trim()));
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.EXPORT_KEY_EXPIRED);
    }
    Map<String, Object> meta = objectMapper.readValue(raw, new TypeReference<>() {});
    long owner = ((Number) meta.getOrDefault("userId", 0L)).longValue();
    if (owner != me) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.EXPORT_DENIED);
    }
    String path = String.valueOf(meta.getOrDefault("path", ""));
    String name = String.valueOf(meta.getOrDefault("name", "attendance.csv"));
    Path file = Path.of(path);
    if (!Files.isRegularFile(file)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.EXPORT_KEY_EXPIRED);
    }
    InputStream in = Files.newInputStream(file);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .contentLength(Files.size(file))
        .body(new InputStreamResource(in));
  }

  private void notifyProgress(long userId, String body) {
    NotifySendPublisher n = notifyPublisher.getIfAvailable();
    if (n == null) {
      return;
    }
    n.publish(
        new NotifySendEvent(
            IdGenerator.nextId() + "",
            NotifySendEvent.CHANNEL_DESKTOP,
            List.of(userId),
            "导出签到",
            body,
            Map.of("kind", "attendanceExport")));
  }

  private static List<Long> parseUserIds(String raw) {
    LinkedHashSet<Long> out = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String t = raw.trim();
    if (t.startsWith("[") && t.endsWith("]")) {
      t = t.substring(1, t.length() - 1);
    }
    for (String part : t.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        long id = Long.parseLong(part.trim());
        if (id > 0) {
          out.add(id);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    return new ArrayList<>(out);
  }

  private static String[] parseDateRange(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
    }
    String t = raw.trim();
    if (t.startsWith("[") && t.endsWith("]")) {
      t = t.substring(1, t.length() - 1);
    }
    String[] parts = t.split("[,~]");
    if (parts.length < 2) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    LocalDate start;
    LocalDate end;
    try {
      start = LocalDate.parse(parts[0].trim());
      end = LocalDate.parse(parts[1].trim());
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    if (end.isBefore(start)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_RANGE, MAX_DAYS);
    }
    return new String[] {start.toString(), end.toString()};
  }

  private static String parseShift(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
    }
    String t = raw.trim();
    if (t.startsWith("[") && t.endsWith("]")) {
      t = t.substring(1, t.length() - 1);
    }
    String[] parts = t.split("[,~]");
    if (parts.length < 2) {
      parts = t.split("\\s+");
    }
    if (parts.length < 2) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    LocalTime a = parseHm(parts[0].trim());
    LocalTime b = parseHm(parts[1].trim());
    if (a == null || b == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    return a.format(DateTimeFormatter.ofPattern("HH:mm"))
        + ","
        + b.format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  private static LocalTime parseHm(String s) {
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
}
