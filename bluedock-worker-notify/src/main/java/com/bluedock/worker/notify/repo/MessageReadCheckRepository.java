package com.bluedock.worker.notify.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageReadCheckRepository {
  private final JdbcTemplate jdbc;

  public MessageReadCheckRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 用户是否已读该消息（read_at 非空）。 */
  public boolean isRead(long messageId, long userId) {
    if (messageId <= 0 || userId <= 0) {
      return false;
    }
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_dialog_message_reads
            WHERE message_id = ? AND user_id = ? AND read_at IS NOT NULL
            """,
            Integer.class,
            messageId,
            userId);
    return n != null && n > 0;
  }

  /** 读回执静默（会话免打扰写入）；@提及应已清零。 */
  public boolean isSilent(long messageId, long userId) {
    if (messageId <= 0 || userId <= 0) {
      return false;
    }
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_dialog_message_reads
            WHERE message_id = ? AND user_id = ? AND IFNULL(is_silent, 0) = 1
            """,
            Integer.class,
            messageId,
            userId);
    return n != null && n > 0;
  }
}
