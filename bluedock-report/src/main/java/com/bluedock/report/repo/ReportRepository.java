package com.bluedock.report.repo;

import com.bluedock.report.domain.Report;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
  private static final RowMapper<Report> MAPPER = ReportRepository::mapRow;

  private final JdbcTemplate jdbc;

  public ReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Report r) {
    jdbc.update(
        """
        INSERT INTO bluedock_reports
          (id, sign, title, type, user_id, content, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        r.getId(),
        r.getSign(),
        r.getTitle(),
        r.getType(),
        r.getUserId(),
        r.getContent(),
        Timestamp.valueOf(r.getCreatedAt()),
        Timestamp.valueOf(r.getUpdatedAt()));
  }

  public void update(Report r) {
    jdbc.update(
        """
        UPDATE bluedock_reports
        SET title = ?, type = ?, content = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        r.getTitle(),
        r.getType(),
        r.getContent(),
        Timestamp.valueOf(r.getUpdatedAt()),
        r.getId());
  }

  public Optional<Report> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, sign, title, type, user_id, content, created_at, updated_at,
                   NULL AS is_read, NULL AS receive_at
            FROM bluedock_reports
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public boolean existsSign(long userId, String type, String sign) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_reports
            WHERE user_id = ? AND type = ? AND sign = ? AND deleted_at IS NULL
            """,
            Integer.class,
            userId,
            type,
            sign);
    return n != null && n > 0;
  }

  public List<Report> listMine(long userId, String type, int limit, int offset) {
    if (type != null && !type.isBlank()) {
      return jdbc.query(
          """
          SELECT id, sign, title, type, user_id, content, created_at, updated_at,
                 NULL AS is_read, NULL AS receive_at
          FROM bluedock_reports
          WHERE user_id = ? AND type = ? AND deleted_at IS NULL
          ORDER BY created_at DESC
          LIMIT ? OFFSET ?
          """,
          MAPPER,
          userId,
          type,
          limit,
          offset);
    }
    return jdbc.query(
        """
        SELECT id, sign, title, type, user_id, content, created_at, updated_at,
               NULL AS is_read, NULL AS receive_at
        FROM bluedock_reports
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT ? OFFSET ?
        """,
        MAPPER,
        userId,
        limit,
        offset);
  }

  public List<Report> listReceived(long userId, String type, String status, int limit, int offset) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT r.id, r.sign, r.title, r.type, r.user_id, r.content, r.created_at, r.updated_at,
                   rr.is_read AS is_read, rr.receive_at AS receive_at
            FROM bluedock_reports r
            INNER JOIN bluedock_report_receives rr ON rr.report_id = r.id AND rr.user_id = ?
            WHERE r.deleted_at IS NULL
            """);
    if (type != null && !type.isBlank()) {
      sql.append(" AND r.type = ?");
    }
    if ("unread".equals(status)) {
      sql.append(" AND rr.is_read = 0");
    } else if ("read".equals(status)) {
      sql.append(" AND rr.is_read = 1");
    }
    sql.append(" ORDER BY r.created_at DESC LIMIT ? OFFSET ?");

    if (type != null && !type.isBlank()) {
      return jdbc.query(sql.toString(), MAPPER, userId, type, limit, offset);
    }
    return jdbc.query(sql.toString(), MAPPER, userId, limit, offset);
  }

  public void insertReceive(long id, long reportId, long userId, LocalDateTime at) {
    jdbc.update(
        """
        INSERT INTO bluedock_report_receives
          (id, report_id, user_id, is_read, receive_at, created_at, updated_at)
        VALUES (?, ?, ?, 0, ?, ?, ?)
        """,
        id,
        reportId,
        userId,
        Timestamp.valueOf(at),
        Timestamp.valueOf(at),
        Timestamp.valueOf(at));
  }

  public void deleteReceives(long reportId) {
    jdbc.update("DELETE FROM bluedock_report_receives WHERE report_id = ?", reportId);
  }

  public List<Long> listReceiveUserIds(long reportId) {
    return jdbc.queryForList(
        "SELECT user_id FROM bluedock_report_receives WHERE report_id = ? ORDER BY user_id",
        Long.class,
        reportId);
  }

  public boolean isReceiver(long reportId, long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_report_receives WHERE report_id = ? AND user_id = ?",
            Integer.class,
            reportId,
            userId);
    return n != null && n > 0;
  }

  public void markRead(long reportId, long userId, boolean read) {
    jdbc.update(
        """
        UPDATE bluedock_report_receives
        SET is_read = ?, updated_at = ?
        WHERE report_id = ? AND user_id = ?
        """,
        read ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        reportId,
        userId);
  }

  public void markReadBatch(List<Long> rids, long userId) {
    if (rids == null || rids.isEmpty()) {
      return;
    }
    for (Long reportId : rids) {
      if (reportId != null) {
        markRead(reportId, userId, true);
      }
    }
  }

  public int countUnread(long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_report_receives
            WHERE user_id = ? AND is_read = 0
            """,
            Integer.class,
            userId);
    return n == null ? 0 : n;
  }

  public Optional<Long> lastSubmitterReceive(long userId) {
    var list =
        jdbc.query(
            """
            SELECT rr.user_id
            FROM bluedock_reports r
            INNER JOIN bluedock_report_receives rr ON rr.report_id = r.id
            WHERE r.user_id = ? AND r.deleted_at IS NULL
            ORDER BY r.created_at DESC, rr.id ASC
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userId);
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findLinkByReportIdAndUserId(long reportId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT id, report_id, user_id, code, open_count
            FROM bluedock_report_links
            WHERE report_id = ? AND user_id = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("reportId", rs.getLong("report_id"));
              m.put("userId", rs.getLong("user_id"));
              m.put("code", rs.getString("code"));
              m.put("openCount", rs.getInt("open_count"));
              return m;
            },
            reportId,
            userId);
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findLinkByCode(String code) {
    var list =
        jdbc.query(
            """
            SELECT id, report_id, user_id, code, open_count
            FROM bluedock_report_links
            WHERE code = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("reportId", rs.getLong("report_id"));
              m.put("userId", rs.getLong("user_id"));
              m.put("code", rs.getString("code"));
              m.put("openCount", rs.getInt("open_count"));
              return m;
            },
            code);
    return list.stream().findFirst();
  }

  public void insertLink(long id, long reportId, long userId, String code, LocalDateTime at) {
    jdbc.update(
        """
        INSERT INTO bluedock_report_links
          (id, report_id, user_id, code, open_count, created_at, updated_at)
        VALUES (?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        reportId,
        userId,
        code,
        Timestamp.valueOf(at),
        Timestamp.valueOf(at));
  }

  public void updateLinkCode(long id, String code, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_report_links SET code = ?, updated_at = ? WHERE id = ?
        """,
        code,
        Timestamp.valueOf(at),
        id);
  }

  public void incrementLinkOpenCount(String code) {
    jdbc.update(
        """
        UPDATE bluedock_report_links SET open_count = open_count + 1, updated_at = ? WHERE code = ?
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        code);
  }

  public Optional<Map<String, Object>> findAnalysis(long reportId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT id, report_id, user_id, analysis_text, model, meta
            FROM bluedock_report_ai_analyses
            WHERE report_id = ? AND user_id = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("reportId", rs.getLong("report_id"));
              m.put("userId", rs.getLong("user_id"));
              m.put("text", rs.getString("analysis_text") == null ? "" : rs.getString("analysis_text"));
              m.put("model", rs.getString("model") == null ? "" : rs.getString("model"));
              m.put("meta", rs.getString("meta"));
              return m;
            },
            reportId,
            userId);
    return list.stream().findFirst();
  }

  public void upsertAnalysis(
      long id, long reportId, long userId, String text, String model, String meta, LocalDateTime at) {
    Optional<Map<String, Object>> exist = findAnalysis(reportId, userId);
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_report_ai_analyses
          SET analysis_text = ?, model = ?, meta = ?, updated_at = ?
          WHERE report_id = ? AND user_id = ?
          """,
          text,
          model,
          meta,
          Timestamp.valueOf(at),
          reportId,
          userId);
    } else {
      jdbc.update(
          """
          INSERT INTO bluedock_report_ai_analyses
            (id, report_id, user_id, analysis_text, model, meta, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id,
          reportId,
          userId,
          text,
          model,
          meta,
          Timestamp.valueOf(at),
          Timestamp.valueOf(at));
    }
  }

  private static Report mapRow(ResultSet rs, int rowNum) throws SQLException {
    Report r = new Report();
    r.setId(rs.getLong("id"));
    r.setSign(rs.getString("sign"));
    r.setTitle(rs.getString("title"));
    r.setType(rs.getString("type"));
    r.setUserId(rs.getLong("user_id"));
    r.setContent(rs.getString("content"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    if (c != null) {
      r.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      r.setUpdatedAt(u.toLocalDateTime());
    }
    Object read = rs.getObject("is_read");
    if (read != null) {
      r.setRead(rs.getInt("is_read"));
    }
    Timestamp ra = rs.getTimestamp("receive_at");
    if (ra != null) {
      r.setReceiveAt(ra.toLocalDateTime());
    }
    return r;
  }

  /**
   * 当前用户作为负责人（owner=1）的任务名列表。
   *
   * @param completed true=周期内已完成；false=未完成且计划时间与窗口相交（无计划时间也纳入未完成）
   */
  public List<String> listOwnerTaskNames(
      long userId, LocalDateTime windowStart, LocalDateTime windowEnd, boolean completed) {
    String sql;
    if (completed) {
      sql =
          """
          SELECT t.name
          FROM bluedock_tasks t
          INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
          WHERE t.deleted_at IS NULL AND t.archived_at IS NULL
            AND t.complete_at IS NOT NULL
            AND t.complete_at >= ? AND t.complete_at <= ?
          ORDER BY t.complete_at DESC, t.id DESC
          LIMIT 200
          """;
    } else {
      sql =
          """
          SELECT t.name
          FROM bluedock_tasks t
          INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
          WHERE t.deleted_at IS NULL AND t.archived_at IS NULL
            AND t.complete_at IS NULL
            AND (
              (t.start_at IS NOT NULL AND t.end_at IS NOT NULL
                AND t.start_at <= ? AND t.end_at >= ?)
              OR (t.start_at IS NULL AND t.end_at IS NULL)
              OR (t.end_at IS NOT NULL AND t.end_at >= ? AND t.end_at <= ?)
              OR (t.start_at IS NOT NULL AND t.start_at >= ? AND t.start_at <= ?)
            )
          ORDER BY t.end_at IS NULL, t.end_at ASC, t.id DESC
          LIMIT 200
          """;
    }
    if (completed) {
      return jdbc.query(
          sql,
          (rs, i) -> rs.getString(1),
          userId,
          Timestamp.valueOf(windowStart),
          Timestamp.valueOf(windowEnd));
    }
    return jdbc.query(
        sql,
        (rs, i) -> rs.getString(1),
        userId,
        Timestamp.valueOf(windowEnd),
        Timestamp.valueOf(windowStart),
        Timestamp.valueOf(windowStart),
        Timestamp.valueOf(windowEnd),
        Timestamp.valueOf(windowStart),
        Timestamp.valueOf(windowEnd));
  }
}

