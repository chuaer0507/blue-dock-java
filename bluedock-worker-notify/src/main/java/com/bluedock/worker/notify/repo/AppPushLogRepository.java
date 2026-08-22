package com.bluedock.worker.notify.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppPushLogRepository {
  private final JdbcTemplate jdbc;

  public AppPushLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      long userId,
      String platform,
      String alias,
      String title,
      String body,
      String requestBody,
      String responseBody,
      String status,
      String skipReason,
      String eventId,
      long messageId,
      long dialogId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_app_push_logs
          (id, user_id, platform, alias, title, body, request_body, response_body,
           status, skip_reason, event_id, message_id, dialog_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        userId,
        nullToEmpty(platform),
        nullToEmpty(alias),
        truncate(title, 500),
        truncate(body, 2000),
        truncate(requestBody, 8000),
        truncate(responseBody, 8000),
        nullToEmpty(status),
        nullToEmpty(skipReason),
        nullToEmpty(eventId),
        messageId,
        dialogId,
        Timestamp.valueOf(now));
  }

  /** 批量写入（同一次投递多别名）。 */
  public void insertBatch(
      List<Long> userIds,
      String platform,
      List<String> aliases,
      String title,
      String body,
      String requestBody,
      String responseBody,
      String status,
      String skipReason,
      String eventId,
      long messageId,
      long dialogId) {
    if (userIds == null || userIds.isEmpty()) {
      insert(
          0L,
          platform,
          aliases == null || aliases.isEmpty() ? "" : String.join(",", aliases),
          title,
          body,
          requestBody,
          responseBody,
          status,
          skipReason,
          eventId,
          messageId,
          dialogId);
      return;
    }
    String aliasJoined = aliases == null || aliases.isEmpty() ? "" : String.join(",", aliases);
    for (Long userId : userIds) {
      if (userId == null) {
        continue;
      }
      insert(
          userId,
          platform,
          aliasJoined,
          title,
          body,
          requestBody,
          responseBody,
          status,
          skipReason,
          eventId,
          messageId,
          dialogId);
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
