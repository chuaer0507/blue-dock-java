package com.bluedock.user.service;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 个人年度数据报告（当前年或指定 year；聚合任务/项目/文件/聊天）。 */
@Service
public class UserAnnualReportService {
  private final UserAccountRepository users;
  private final JdbcTemplate jdbc;

  public UserAnnualReportService(UserAccountRepository users, JdbcTemplate jdbc) {
    this.users = users;
    this.jdbc = jdbc;
  }

  public Map<String, Object> report(Integer yearParam) {
    long me = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(me)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    int year = resolveYear(yearParam);
    LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
    LocalDateTime yearEnd = LocalDate.of(year, 12, 31).atTime(23, 59, 59, 999_000_000);
    Map<String, Object> timeline = userTimeline(me);
    LocalDateTime createdAt = toLocalDateTime(timeline.get("createdAt"));
    LocalDateTime onlineAt = toLocalDateTime(timeline.get("onlineAt"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("year", year);
    out.put("user", userBlock(user));
    out.put("hireDate", formatDate(createdAt));
    out.put("tenureDays", tenureDays(createdAt));
    out.put("latestOnlineTime", latestOnlineInYear(onlineAt, year));
    out.put("longestChat", longestChat(me, yearStart, yearEnd));
    out.put("chatAiNum", chatAiNum(me, yearStart, yearEnd));
    out.put("fileCreatedNum", fileCreatedNum(me, yearStart, yearEnd));
    out.put("projects", projects(me, yearStart, yearEnd));
    out.put("tasks", tasks(me, yearStart, yearEnd));
    return out;
  }

  private Map<String, Object> userTimeline(long userId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT created_at AS createdAt, online_at AS onlineAt FROM bluedock_users WHERE id = ?",
            userId);
    if (rows.isEmpty()) {
      return Map.of();
    }
    return rows.get(0);
  }

  private static LocalDateTime toLocalDateTime(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof LocalDateTime ldt) {
      return ldt;
    }
    if (v instanceof java.sql.Timestamp ts) {
      return ts.toLocalDateTime();
    }
    if (v instanceof java.util.Date d) {
      return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
    }
    return null;
  }

  private static int resolveYear(Integer yearParam) {
    int current = Year.now().getValue();
    if (yearParam == null) {
      return current;
    }
    if (yearParam < 2000 || yearParam > current + 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_ANNUAL_YEAR_INVALID);
    }
    return yearParam;
  }

  private static Map<String, Object> userBlock(UserAccount user) {
    Map<String, Object> u = new LinkedHashMap<>();
    u.put("userId", user.getUserId());
    u.put("email", user.getEmail() == null ? "" : user.getEmail());
    u.put("nickname", user.getNickname() == null ? "" : user.getNickname());
    u.put("avatar", user.getUserImage() == null ? "" : user.getUserImage());
    return u;
  }

  private static String formatDate(LocalDateTime at) {
    return at == null ? "" : at.toLocalDate().toString();
  }

  private static long tenureDays(LocalDateTime createdAt) {
    if (createdAt == null) {
      return 0L;
    }
    return Math.max(0L, ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now()));
  }

  private static String latestOnlineInYear(LocalDateTime online, int year) {
    if (online == null || online.getYear() != year) {
      return "";
    }
    return online.toString().replace('T', ' ');
  }

  private Map<String, Object> longestChat(long userId, LocalDateTime from, LocalDateTime to) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT d.id AS dialogId, d.name AS dialogName, d.type AS dialogType,
                   d.group_type AS dialogGroupType, d.avatar AS avatar,
                   COUNT(m.id) AS chatNum
            FROM bluedock_dialog_messages m
            INNER JOIN bluedock_dialogs d ON d.id = m.dialog_id AND d.deleted_at IS NULL
            WHERE m.user_id = ? AND m.deleted_at IS NULL
              AND m.created_at >= ? AND m.created_at <= ?
            GROUP BY d.id, d.name, d.type, d.group_type, d.avatar
            ORDER BY chatNum DESC, d.id DESC
            LIMIT 1
            """,
            userId,
            from,
            to);
    if (rows.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> raw = rows.get(0);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dialogId", raw.get("dialogId"));
    out.put("dialogName", nullToEmpty(raw.get("dialogName")));
    out.put("dialogType", nullToEmpty(raw.get("dialogType")));
    out.put("dialogGroupType", nullToEmpty(raw.get("dialogGroupType")));
    out.put("avatar", nullToEmpty(raw.get("avatar")));
    out.put("chatNum", asLong(raw.get("chatNum")));
    return out;
  }

  private long chatAiNum(long userId, LocalDateTime from, LocalDateTime to) {
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1)
            FROM bluedock_dialog_messages m
            INNER JOIN bluedock_dialog_users du ON du.dialog_id = m.dialog_id
            INNER JOIN bluedock_users u ON u.id = du.user_id
            WHERE m.user_id = ? AND m.deleted_at IS NULL
              AND m.created_at >= ? AND m.created_at <= ?
              AND u.is_bot = 1 AND IFNULL(u.email, '') LIKE 'ai-%@bot.system'
            """,
            Long.class,
            userId,
            from,
            to);
    return n == null ? 0L : n;
  }

  private long fileCreatedNum(long userId, LocalDateTime from, LocalDateTime to) {
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_files
            WHERE created_user_id = ? AND deleted_at IS NULL
              AND created_at >= ? AND created_at <= ?
            """,
            Long.class,
            userId,
            from,
            to);
    return n == null ? 0L : n;
  }

  private List<Map<String, Object>> projects(long userId, LocalDateTime from, LocalDateTime to) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT p.id, p.name FROM bluedock_projects p
            WHERE p.deleted_at IS NULL AND (
              EXISTS (
                SELECT 1 FROM bluedock_project_users pu
                WHERE pu.project_id = p.id AND pu.user_id = ?
                  AND pu.created_at >= ? AND pu.created_at <= ?
              )
              OR EXISTS (
                SELECT 1 FROM bluedock_task_users tu
                WHERE tu.project_id = p.id AND tu.user_id = ?
                  AND tu.created_at >= ? AND tu.created_at <= ?
              )
            )
            ORDER BY p.id DESC
            LIMIT 100
            """,
            userId,
            from,
            to,
            userId,
            from,
            to);
    List<Map<String, Object>> out = new ArrayList<>(rows.size());
    for (Map<String, Object> r : rows) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", r.get("id"));
      item.put("name", nullToEmpty(r.get("name")));
      out.add(item);
    }
    return out;
  }

  private Map<String, Object> tasks(long userId, LocalDateTime from, LocalDateTime to) {
    long total = countOwnerTasks(userId, from, to, null);
    long completed = countOwnerTasks(userId, from, to, true);
    long overtime =
        asLong(
            jdbc.queryForObject(
                """
                SELECT COUNT(1)
                FROM bluedock_tasks t
                INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
                WHERE t.deleted_at IS NULL
                  AND t.created_at >= ? AND t.created_at <= ?
                  AND t.end_at IS NOT NULL
                  AND (t.complete_at IS NULL OR t.complete_at > t.end_at)
                """,
                Long.class,
                userId,
                from,
                to));
    Map<String, Object> longest = durationTask(userId, from, to, false);
    Map<String, Object> fastest = durationTask(userId, from, to, true);
    List<Map<String, Object>> monthCompleted =
        jdbc.queryForList(
            """
            SELECT MONTH(t.complete_at) AS month, COUNT(t.id) AS num
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
            WHERE t.deleted_at IS NULL AND t.complete_at IS NOT NULL
              AND t.complete_at >= ? AND t.complete_at <= ?
            GROUP BY MONTH(t.complete_at)
            ORDER BY month ASC
            """,
            userId,
            from,
            to);
    List<Map<String, Object>> months = new ArrayList<>();
    for (Map<String, Object> m : monthCompleted) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("month", asLong(m.get("month")));
      item.put("num", asLong(m.get("num")));
      months.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", total);
    out.put("completed", completed);
    out.put("overtime", overtime);
    out.put("longestTask", longest);
    out.put("fastestTask", fastest);
    out.put("monthCompletedTask", months);
    return out;
  }

  private long countOwnerTasks(
      long userId, LocalDateTime from, LocalDateTime to, Boolean completedOnly) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(1)
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
            WHERE t.deleted_at IS NULL
              AND t.created_at >= ? AND t.created_at <= ?
            """);
    if (Boolean.TRUE.equals(completedOnly)) {
      sql.append(" AND t.complete_at IS NOT NULL");
    }
    Long n = jdbc.queryForObject(sql.toString(), Long.class, userId, from, to);
    return n == null ? 0L : n;
  }

  private Map<String, Object> durationTask(
      long userId, LocalDateTime from, LocalDateTime to, boolean fastest) {
    String order = fastest ? "ASC" : "DESC";
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT t.id, t.flow_item_name AS flowItemName, t.name AS taskName,
                   p.name AS projectName, c.name AS projectColumnName,
                   t.start_at AS startAt, t.end_at AS endAt,
                   t.complete_at AS completeAt, t.created_at AS createdAt,
                   TIMESTAMPDIFF(MINUTE, t.start_at, t.complete_at) AS duration
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ? AND tu.owner = 1
            LEFT JOIN bluedock_projects p ON p.id = t.project_id
            LEFT JOIN bluedock_project_columns c ON c.id = t.column_id
            WHERE t.deleted_at IS NULL
              AND t.created_at >= ? AND t.created_at <= ?
              AND t.start_at IS NOT NULL AND t.complete_at IS NOT NULL
            ORDER BY duration 
            """
                + order
                + ", t.id DESC LIMIT 1",
            userId,
            from,
            to);
    if (rows.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> r = rows.get(0);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", r.get("id"));
    out.put("flowItemName", nullToEmpty(r.get("flowItemName")));
    out.put("taskName", nullToEmpty(r.get("taskName")));
    out.put("projectName", nullToEmpty(r.get("projectName")));
    out.put("projectColumnName", nullToEmpty(r.get("projectColumnName")));
    out.put("startAt", r.get("startAt"));
    out.put("endAt", r.get("endAt"));
    out.put("completeAt", r.get("completeAt"));
    out.put("createdAt", r.get("createdAt"));
    out.put("duration", asLong(r.get("duration")));
    return out;
  }

  private static String nullToEmpty(Object v) {
    return v == null ? "" : String.valueOf(v);
  }

  private static long asLong(Object v) {
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
