package com.bluedock.worker.notify.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.export.ApproveExportBridge;
import com.bluedock.common.export.ExportNotifyEvent;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExportRunHandler {
  private static final Logger log = LoggerFactory.getLogger(ExportRunHandler.class);
  private static final Duration DOWN_TTL = Duration.ofHours(24);
  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectProvider<ApproveExportBridge> approveBridge;

  public ExportRunHandler(
      JdbcTemplate jdbc,
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      KafkaTemplate<String, String> kafka,
      ObjectProvider<ApproveExportBridge> approveBridge) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.kafka = kafka;
    this.approveBridge = approveBridge;
  }

  public void handle(ExportRunEvent event) {
    if (event == null || event.eventId() == null) {
      return;
    }
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.exportIdempotency(event.eventId()), "1", Duration.ofDays(2));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      if (ExportRunEvent.KIND_TASK_OVERDUE.equals(event.kind())) {
        runOverdue(event);
      } else if (ExportRunEvent.KIND_ATTENDANCE.equals(event.kind())) {
        runAttendance(event);
      } else if (ExportRunEvent.KIND_APPROVE.equals(event.kind())) {
        runApprove(event);
      } else {
        runStats(event);
      }
    } catch (Exception e) {
      log.warn("export failed eventId={}: {}", event.eventId(), e.toString());
      notify(
          event.requesterUserId(),
          "导出失败",
          "导出失败：" + e.getMessage(),
          Map.of());
    }
  }

  private void runApprove(ExportRunEvent event) throws Exception {
    ApproveExportBridge bridge = approveBridge.getIfAvailable();
    if (bridge == null || !bridge.available()) {
      notify(event.requesterUserId(), "导出审批失败", "未安装审批插件，无法导出", Map.of());
      return;
    }
    String processName = event.processName() == null ? "" : event.processName().trim();
    if (processName.isEmpty()) {
      notify(event.requesterUserId(), "导出审批失败", "流程分类不能为空", Map.of());
      return;
    }
    LocalDate start = LocalDate.parse(event.timeStart());
    LocalDate end = LocalDate.parse(event.timeEnd());
    String status = event.status() == null ? "" : event.status().trim();
    List<Map<String, Object>> rows = bridge.query(processName, status, start, end);
    if (rows == null || rows.isEmpty()) {
      notify(event.requesterUserId(), "导出审批已完成", "没有任何数据", Map.of());
      return;
    }
    String fileName = "approve_" + System.currentTimeMillis() + ".csv";
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "bluedock-export");
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName);
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write('\ufeff');
      w.write("审批ID,流程分类,标题,发起人,状态,创建时间,完成时间");
      w.newLine();
      for (Map<String, Object> row : rows) {
        w.write(
            csv(
                nz(row.get("id")),
                nz(row.get("processName")),
                nz(row.get("title")),
                nz(row.get("requesterNickname")),
                nz(row.get("status")),
                nz(row.get("createdAt")),
                nz(row.get("completedAt"))));
        w.newLine();
      }
    }
    String key = UUID.randomUUID().toString().replace("-", "");
    storeDown(key, event.requesterUserId(), file, fileName);
    notify(
        event.requesterUserId(),
        "导出审批已完成",
        "导出审批已完成，点击下载：" + approveDownloadUrl(key),
        Map.of("url", approveDownloadUrl(key), "key", key, "name", fileName));
  }

  private void runAttendance(ExportRunEvent event) throws Exception {
    List<Long> userIds = event.userIds() == null ? List.of() : event.userIds();
    if (userIds.isEmpty()) {
      notify(event.requesterUserId(), "导出签到已完成", "没有任何数据", Map.of());
      return;
    }
    LocalDate start = LocalDate.parse(event.timeStart());
    LocalDate end = LocalDate.parse(event.timeEnd());
    String shift = event.timeType() == null || event.timeType().isBlank() ? "-" : event.timeType();
    String placeholders = userIds.stream().map(x -> "?").collect(Collectors.joining(","));
    List<Object> args = new ArrayList<>(userIds);
    args.add(java.sql.Date.valueOf(start));
    args.add(java.sql.Date.valueOf(end));
    String sql =
        """
        SELECT r.user_id AS userId, r.attendance_date AS attendanceDate, r.times, u.nickname
        FROM bluedock_user_attendance_records r
        LEFT JOIN bluedock_users u ON u.id = r.user_id
        WHERE r.user_id IN (%s)
          AND r.attendance_date BETWEEN ? AND ?
        ORDER BY r.user_id ASC, r.attendance_date ASC
        """
            .formatted(placeholders);
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
    if (rows.isEmpty()) {
      notify(event.requesterUserId(), "导出签到已完成", "没有任何数据", Map.of());
      return;
    }
    String fileName = "attendance_" + System.currentTimeMillis() + ".csv";
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "bluedock-export");
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName);
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write('\ufeff');
      w.write("用户ID,昵称,日期,上班打卡,下班打卡,班次,打卡明细");
      w.newLine();
      for (Map<String, Object> row : rows) {
        long userId = ((Number) row.get("userId")).longValue();
        String nick = nz(row.get("nickname"));
        if ("-".equals(nick)) {
          nick = String.valueOf(userId);
        }
        String day = formatCheckDate(row.get("attendanceDate"));
        List<Map<String, Object>> times = parseTimes(row.get("times"));
        String inAt = firstSection(times, "in");
        String outAt = lastSection(times, "out");
        w.write(csv(userId, nick, day, inAt, outAt, shift, detailOf(times)));
        w.newLine();
      }
    }
    String key = UUID.randomUUID().toString().replace("-", "");
    storeDown(key, event.requesterUserId(), file, fileName);
    notify(
        event.requesterUserId(),
        "导出签到已完成",
        "导出签到已完成，点击下载：" + attendanceDownloadUrl(key),
        Map.of("url", attendanceDownloadUrl(key), "key", key, "name", fileName));
  }

  private void runStats(ExportRunEvent event) throws Exception {
    List<Long> userIds = event.userIds() == null ? List.of() : event.userIds();
    if (userIds.isEmpty()) {
      notify(event.requesterUserId(), "导出任务统计已完成", "没有任何数据", Map.of());
      return;
    }
    LocalDateTime start = LocalDate.parse(event.timeStart()).atStartOfDay();
    LocalDateTime end = LocalDate.parse(event.timeEnd()).atTime(23, 59, 59);
    String placeholders = userIds.stream().map(x -> "?").collect(Collectors.joining(","));
    List<Object> args = new ArrayList<>(userIds);
    String timeClause;
    if (ExportRunEvent.TIME_CREATED.equals(event.timeType())) {
      timeClause = " AND t.created_at BETWEEN ? AND ? ";
      args.add(Timestamp.valueOf(start));
      args.add(Timestamp.valueOf(end));
    } else {
      // 计划时间与区间相交；无计划但考核期内完成则兜底纳入
      timeClause =
          """
           AND (
             (t.start_at IS NOT NULL AND t.end_at IS NOT NULL
               AND t.start_at <= ? AND t.end_at >= ?)
             OR (
               (t.start_at IS NULL OR t.end_at IS NULL)
               AND t.complete_at IS NOT NULL
               AND t.complete_at BETWEEN ? AND ?
             )
           )
          """;
      args.add(Timestamp.valueOf(end));
      args.add(Timestamp.valueOf(start));
      args.add(Timestamp.valueOf(start));
      args.add(Timestamp.valueOf(end));
    }

    String sql =
        """
        SELECT DISTINCT t.id, t.parent_id AS parentId, t.project_id AS projectId, p.name AS projectName, t.name, t.description,
               t.start_at AS startAt, t.end_at AS endAt, t.complete_at AS completeAt, t.archived_at AS archivedAt, t.flow_item_name AS flowItemName, t.user_id AS userId,
               tu.user_id AS ownerId
        FROM bluedock_tasks t
        INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.owner = 1
        LEFT JOIN bluedock_projects p ON p.id = t.project_id
        WHERE t.deleted_at IS NULL
          AND tu.user_id IN (%s)
        """
            .formatted(placeholders)
            + timeClause
            + " ORDER BY tu.user_id ASC, t.id DESC";

    List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
    if (rows.isEmpty()) {
      notify(event.requesterUserId(), "导出任务统计已完成", "没有任何数据", Map.of());
      return;
    }

    String fileName = "task_stats_" + System.currentTimeMillis() + ".csv";
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "bluedock-export");
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName);
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write('\ufeff');
      w.write(
          "任务ID,父级任务ID,所属项目,任务标题,任务标签,任务开始时间,任务结束时间,完成时间,归档时间,任务计划用时,实际完成用时,超时时间,负责人,创建人,状态");
      w.newLine();
      for (Map<String, Object> row : rows) {
        long taskId = ((Number) row.get("id")).longValue();
        long ownerId = ((Number) row.get("ownerId")).longValue();
        long creatorId = ((Number) row.getOrDefault("userId", 0L)).longValue();
        LocalDateTime startAt = toLdt(row.get("startAt"));
        LocalDateTime endAt = toLdt(row.get("endAt"));
        LocalDateTime completeAt = toLdt(row.get("completeAt"));
        LocalDateTime archivedAt = toLdt(row.get("archivedAt"));
        String status = statusOf(row.get("flowItemName"), completeAt);
        String plan = planDuration(startAt, endAt);
        String actual = actualDuration(startAt, completeAt);
        String over = overDuration(startAt, endAt, completeAt);
        w.write(
            csv(
                taskId,
                parentOrDash(row.get("parentId")),
                nz(row.get("projectName")),
                nz(row.get("name")),
                tagsOf(taskId),
                fmt(startAt),
                fmt(endAt),
                fmt(completeAt),
                fmt(archivedAt),
                plan,
                actual,
                over,
                nick(ownerId) + " (ID: " + ownerId + ")",
                nick(creatorId) + " (ID: " + creatorId + ")",
                status));
        w.newLine();
      }
    }

    String key = UUID.randomUUID().toString().replace("-", "");
    storeDown(key, event.requesterUserId(), file, fileName);
    notify(
        event.requesterUserId(),
        "导出任务统计已完成",
        "导出任务统计已完成，点击下载：" + downUrl(key),
        Map.of("url", downUrl(key), "key", key, "name", fileName));
  }

  private void runOverdue(ExportRunEvent event) throws Exception {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT t.id, t.parent_id AS parentId, t.project_id AS projectId, p.name AS projectName, t.name,
                   t.start_at AS startAt, t.end_at AS endAt, t.user_id AS userId
            FROM bluedock_tasks t
            LEFT JOIN bluedock_projects p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL
              AND t.complete_at IS NULL
              AND t.end_at IS NOT NULL
              AND t.end_at <= ?
            ORDER BY t.end_at ASC
            """,
            Timestamp.valueOf(LocalDateTime.now()));
    if (rows.isEmpty()) {
      notify(event.requesterUserId(), "导出超期任务已完成", "没有任何数据", Map.of());
      return;
    }
    String fileName = "task_overdue_" + System.currentTimeMillis() + ".csv";
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "bluedock-export");
    Files.createDirectories(dir);
    Path file = dir.resolve(fileName);
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write('\ufeff');
      w.write("任务ID,父级任务ID,所属项目,任务标题,任务标签,任务开始时间,任务结束时间,任务计划用时,超时时间,负责人,创建人");
      w.newLine();
      for (Map<String, Object> row : rows) {
        long taskId = ((Number) row.get("id")).longValue();
        long creatorId = ((Number) row.getOrDefault("userId", 0L)).longValue();
        LocalDateTime startAt = toLdt(row.get("startAt"));
        LocalDateTime endAt = toLdt(row.get("endAt"));
        w.write(
            csv(
                taskId,
                parentOrDash(row.get("parentId")),
                nz(row.get("projectName")),
                nz(row.get("name")),
                tagsOf(taskId),
                fmt(startAt),
                fmt(endAt),
                planDuration(startAt, endAt),
                overDuration(startAt, endAt, LocalDateTime.now()),
                ownersOf(taskId),
                nick(creatorId) + " (ID: " + creatorId + ")"));
        w.newLine();
      }
    }
    String key = UUID.randomUUID().toString().replace("-", "");
    storeDown(key, event.requesterUserId(), file, fileName);
    notify(
        event.requesterUserId(),
        "导出超期任务已完成",
        "导出超期任务已完成，点击下载：" + downUrl(key),
        Map.of("url", downUrl(key), "key", key, "name", fileName));
  }

  private void storeDown(String key, long userId, Path file, String name) throws Exception {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("path", file.toAbsolutePath().toString());
    meta.put("userId", userId);
    meta.put("name", name);
    meta.put("size", Files.size(file));
    redis.opsForValue().set(RedisKeys.exportDown(key), objectMapper.writeValueAsString(meta), DOWN_TTL);
  }

  private void notify(long userId, String title, String body, Map<String, Object> data) {
    try {
      NotifySendEvent event =
          new NotifySendEvent(
              IdGenerator.nextId() + "",
              NotifySendEvent.CHANNEL_DESKTOP,
              List.of(userId),
              title,
              body,
              data);
      kafka.send(
          com.bluedock.common.kafka.KafkaTopics.NOTIFY_SEND,
          event.eventId(),
          objectMapper.writeValueAsString(event));
    } catch (Exception e) {
      log.warn("export desktop notify failed: {}", e.toString());
    }
    try {
      String notifyEventId = IdGenerator.nextId() + "";
      ExportNotifyEvent dm =
          new ExportNotifyEvent(notifyEventId, userId, title == null ? "" : title, body == null ? "" : body);
      kafka.send(
          com.bluedock.common.kafka.KafkaTopics.EXPORT_NOTIFY,
          notifyEventId,
          objectMapper.writeValueAsString(dm));
    } catch (Exception e) {
      log.warn("export system-msg notify failed: {}", e.toString());
    }
  }

  private static String downUrl(String key) {
    return "/api/project/task/download?key=" + key;
  }

  private static String attendanceDownloadUrl(String key) {
    return "/api/system/attendance/download?key=" + key;
  }

  private static String approveDownloadUrl(String key) {
    return "/api/approve/download?key=" + key;
  }

  private List<Map<String, Object>> parseTimes(Object timesRaw) {
    if (timesRaw == null) {
      return List.of();
    }
    try {
      List<Map<String, Object>> list =
          objectMapper.readValue(
              String.valueOf(timesRaw),
              new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
      return list == null ? List.of() : list;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static String firstSection(List<Map<String, Object>> times, String section) {
    for (Map<String, Object> t : times) {
      if (section.equalsIgnoreCase(String.valueOf(t.getOrDefault("section", "")))) {
        return nz(t.get("at"));
      }
    }
    return times.isEmpty() ? "-" : nz(times.get(0).get("at"));
  }

  private static String lastSection(List<Map<String, Object>> times, String section) {
    for (int i = times.size() - 1; i >= 0; i--) {
      Map<String, Object> t = times.get(i);
      if (section.equalsIgnoreCase(String.valueOf(t.getOrDefault("section", "")))) {
        return nz(t.get("at"));
      }
    }
    return times.isEmpty() ? "-" : nz(times.get(times.size() - 1).get("at"));
  }

  private static String detailOf(List<Map<String, Object>> times) {
    if (times.isEmpty()) {
      return "-";
    }
    List<String> parts = new ArrayList<>();
    for (Map<String, Object> t : times) {
      parts.add(
          nz(t.get("at"))
              + "("
              + nz(t.get("mode"))
              + "/"
              + nz(t.get("section"))
              + ")");
    }
    return String.join("; ", parts);
  }

  private static String formatCheckDate(Object o) {
    if (o == null) {
      return "-";
    }
    if (o instanceof java.sql.Date d) {
      return d.toLocalDate().toString();
    }
    if (o instanceof LocalDate ld) {
      return ld.toString();
    }
    String s = String.valueOf(o);
    return s.length() >= 10 ? s.substring(0, 10) : s;
  }

  private String tagsOf(long taskId) {
    List<String> names =
        jdbc.query(
            """
            SELECT pt.name FROM bluedock_task_tags tt
            INNER JOIN bluedock_project_tags pt ON pt.id = tt.tag_id AND pt.deleted_at IS NULL
            WHERE tt.task_id = ?
            ORDER BY pt.sort ASC, pt.id ASC
            """,
            (rs, i) -> rs.getString(1),
            taskId);
    return names.isEmpty() ? "-" : String.join(", ", names);
  }

  private String ownersOf(long taskId) {
    List<Map<String, Object>> owners =
        jdbc.queryForList(
            "SELECT user_id AS userId FROM bluedock_task_users WHERE task_id = ? AND owner = 1 ORDER BY user_id",
            taskId);
    if (owners.isEmpty()) {
      return "-";
    }
    List<String> parts = new ArrayList<>();
    for (Map<String, Object> o : owners) {
      long userId = ((Number) o.get("userId")).longValue();
      parts.add(nick(userId) + " (ID: " + userId + ")");
    }
    return String.join(", ", parts);
  }

  private String nick(long userId) {
    if (userId <= 0) {
      return "-";
    }
    List<String> list =
        jdbc.query(
            "SELECT nickname FROM bluedock_users WHERE id = ?", (rs, i) -> rs.getString(1), userId);
    String n = list.isEmpty() || list.get(0) == null ? "" : list.get(0).trim();
    return n.isEmpty() ? String.valueOf(userId) : n;
  }

  private static String statusOf(Object flowName, LocalDateTime completeAt) {
    String flow = flowName == null ? "" : String.valueOf(flowName);
    if (flow.startsWith("end")) {
      if (flow.contains("取消") || flow.toLowerCase().contains("cancel")) {
        return "已取消";
      }
      return "已完成";
    }
    if (completeAt != null) {
      return "已完成";
    }
    return "未完成";
  }

  private static String planDuration(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null || !end.isAfter(start)) {
      return "-";
    }
    return formatSeconds(Duration.between(start, end).getSeconds());
  }

  private static String actualDuration(LocalDateTime start, LocalDateTime complete) {
    if (complete == null) {
      return "-";
    }
    LocalDateTime s = start == null ? complete : start;
    long sec = Duration.between(s, complete).getSeconds();
    return sec > 0 ? formatSeconds(sec) : "-";
  }

  private static String overDuration(LocalDateTime start, LocalDateTime end, LocalDateTime complete) {
    if (end == null) {
      return "-";
    }
    LocalDateTime done = complete == null ? LocalDateTime.now() : complete;
    if (!done.isAfter(end)) {
      return "-";
    }
    return formatSeconds(Duration.between(end, done).getSeconds());
  }

  private static String formatSeconds(long sec) {
    long s = Math.abs(sec);
    long d = s / 86400;
    long h = (s % 86400) / 3600;
    long m = (s % 3600) / 60;
    if (d > 0) {
      return d + "天" + h + "小时";
    }
    if (h > 0) {
      return h + "小时" + m + "分钟";
    }
    return Math.max(1, m) + "分钟";
  }

  private static Object parentOrDash(Object parentId) {
    if (parentId == null) {
      return "-";
    }
    long p = ((Number) parentId).longValue();
    return p > 0 ? p : "-";
  }

  private static LocalDateTime toLdt(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Timestamp ts) {
      return ts.toLocalDateTime();
    }
    if (o instanceof LocalDateTime ldt) {
      return ldt;
    }
    return null;
  }

  private static String fmt(LocalDateTime t) {
    return t == null ? "-" : t.format(DT);
  }

  private static String nz(Object o) {
    if (o == null) {
      return "-";
    }
    String s = String.valueOf(o).trim();
    return s.isEmpty() ? "-" : s;
  }

  private static String csv(Object... cols) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cols.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(escape(cols[i]));
    }
    return sb.toString();
  }

  private static String escape(Object o) {
    String s = o == null ? "" : String.valueOf(o);
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }
}
