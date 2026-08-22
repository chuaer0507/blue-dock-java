package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.export.ApproveExportBridge;
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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
 * 管理员审批导出：校验后投递 Kafka {@code bluedock.export.run}（kind=approve）。
 *
 * <p>数据由 {@link ApproveExportBridge}（approve 插件）提供；下载 {@code /api/approve/download?key=}。
 */
@Service
public class ApproveExportService {
  private static final int MAX_DAYS = 90;

  private final AdminGuard adminGuard;
  private final ExportRunPublisher exportPublisher;
  private final ObjectProvider<ApproveExportBridge> approveBridge;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public ApproveExportService(
      AdminGuard adminGuard,
      ExportRunPublisher exportPublisher,
      ObjectProvider<ApproveExportBridge> approveBridge,
      ObjectProvider<NotifySendPublisher> notifyPublisher,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.adminGuard = adminGuard;
    this.exportPublisher = exportPublisher;
    this.approveBridge = approveBridge;
    this.notifyPublisher = notifyPublisher;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  /**
   * @param processName 流程分类（必填）
   * @param status 状态（可选）
   * @param dateRaw 日期起止，如 {@code 2026-01-01,2026-01-31}
   */
  public Map<String, Object> export(String processName, String status, String dateRaw) {
    adminGuard.requireAdmin();
    ApproveExportBridge bridge = approveBridge.getIfAvailable();
    if (bridge == null || !bridge.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPROVE_PLUGIN_MISSING);
    }
    if (processName == null || processName.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPROVE_PROCESS_REQUIRED);
    }
    String[] dates = parseDateRange(dateRaw);
    long me = AuthContext.requireUserId();
    String eventId = UUID.randomUUID().toString().replace("-", "");
    String statusNorm = status == null ? "" : status.trim();
    exportPublisher.publish(
        new ExportRunEvent(
            eventId,
            ExportRunEvent.KIND_APPROVE,
            me,
            List.of(),
            dates[0],
            dates[1],
            null,
            processName.trim(),
            statusNorm));
    notifyProgress(me, "正在导出审批数据，请稍等...");
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
    String name = String.valueOf(meta.getOrDefault("name", "approve.csv"));
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
            "导出审批",
            body,
            Map.of("kind", "approveExport")));
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
}
