package com.bluedock.messenger.email;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 未读消息邮件汇总查询。 */
@Repository
public class UnreadEmailNoticeRepository {
  private static final List<String> ALLOWED_TYPES = List.of("text", "file", "record", "meeting");

  private final JdbcTemplate jdbc;

  public UnreadEmailNoticeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 有待汇总未读的用户（分批）。 */
  public List<Long> listCandidateUserIds(
      String dialogType, LocalDateTime start, LocalDateTime end, int limit) {
    String placeholders = String.join(",", Collections.nCopies(ALLOWED_TYPES.size(), "?"));
    List<Object> args = new ArrayList<>();
    args.add(dialogType);
    args.add(Timestamp.valueOf(start));
    args.add(Timestamp.valueOf(end));
    args.addAll(ALLOWED_TYPES);
    args.add(Math.max(1, Math.min(limit, 500)));
    return jdbc.query(
        """
        SELECT DISTINCT r.user_id
        FROM bluedock_dialog_message_reads r
        INNER JOIN bluedock_dialog_messages m ON m.id = r.message_id AND m.deleted_at IS NULL
        INNER JOIN bluedock_dialogs d ON d.id = r.dialog_id AND d.deleted_at IS NULL
        WHERE r.read_at IS NULL
          AND r.is_silent = 0
          AND r.email_sent = 0
          AND d.type = ?
          AND m.created_at >= ? AND m.created_at <= ?
          AND m.type IN (%s)
        ORDER BY r.user_id
        LIMIT ?
        """
            .formatted(placeholders),
        (rs, i) -> rs.getLong(1),
        args.toArray());
  }

  public List<UnreadRow> listUnreadForUser(long userId, String dialogType, int limit) {
    String placeholders = String.join(",", Collections.nCopies(ALLOWED_TYPES.size(), "?"));
    List<Object> args = new ArrayList<>();
    args.add(userId);
    args.add(dialogType);
    args.addAll(ALLOWED_TYPES);
    args.add(Math.max(1, Math.min(limit, 100)));
    return jdbc.query(
        """
        SELECT r.id AS read_id, r.dialog_id, d.name AS dialog_name, d.type AS dialog_type,
               m.id AS message_id, m.type AS message_type, m.body, m.user_id AS sender_id, m.created_at,
               u.nickname AS sender_name
        FROM bluedock_dialog_message_reads r
        INNER JOIN bluedock_dialog_messages m ON m.id = r.message_id AND m.deleted_at IS NULL
        INNER JOIN bluedock_dialogs d ON d.id = r.dialog_id AND d.deleted_at IS NULL
        LEFT JOIN bluedock_users u ON u.id = m.user_id
        WHERE r.user_id = ?
          AND r.read_at IS NULL
          AND r.is_silent = 0
          AND r.email_sent = 0
          AND d.type = ?
          AND m.type IN (%s)
        ORDER BY m.created_at ASC, m.id ASC
        LIMIT ?
        """
            .formatted(placeholders),
        (rs, i) ->
            new UnreadRow(
                rs.getLong("read_id"),
                rs.getLong("dialog_id"),
                rs.getString("dialog_name") == null ? "" : rs.getString("dialog_name"),
                rs.getString("dialog_type") == null ? "" : rs.getString("dialog_type"),
                rs.getLong("message_id"),
                rs.getString("message_type") == null ? "" : rs.getString("message_type"),
                rs.getString("body"),
                rs.getLong("sender_id"),
                rs.getString("sender_name") == null ? "" : rs.getString("sender_name"),
                rs.getTimestamp("created_at") == null
                    ? null
                    : rs.getTimestamp("created_at").toLocalDateTime()),
        args.toArray());
  }

  public String nicknameOf(long userId) {
    List<String> list =
        jdbc.query(
            "SELECT nickname FROM bluedock_users WHERE id = ? LIMIT 1",
            (rs, i) -> rs.getString(1),
            userId);
    return list.isEmpty() || list.get(0) == null ? "" : list.get(0).trim();
  }

  public record UnreadRow(
      long readId,
      long dialogId,
      String dialogName,
      String dialogType,
      long messageId,
      String messageType,
      String body,
      long senderId,
      String senderName,
      LocalDateTime createdAt) {}
}
