package com.bluedock.worker.notify.repo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyUserRepository {
  private final JdbcTemplate jdbc;

  public NotifyUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** userId → email；跳过禁用账号与空邮箱。 */
  public Map<Long, String> emailsByUserIds(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    List<Long> ids = new ArrayList<>(userIds);
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    Object[] args = ids.toArray();
    Map<Long, String> out = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT id, email FROM bluedock_users
        WHERE id IN (%s) AND disable_at IS NULL AND IFNULL(is_bot,0) = 0
          AND email IS NOT NULL AND email <> ''
        """
            .formatted(placeholders),
        rs -> {
          out.put(rs.getLong("id"), rs.getString("email").trim());
        },
        args);
    return out;
  }

  /** 未读汇总发信成功后标记，避免重复投递。 */
  public void markMessageReadsEmailed(Collection<Long> readIds) {
    if (readIds == null || readIds.isEmpty()) {
      return;
    }
    List<Long> ids = new ArrayList<>(readIds);
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    jdbc.update(
        """
        UPDATE bluedock_dialog_message_reads
        SET email = 1
        WHERE id IN (%s) AND email = 0
        """
            .formatted(placeholders),
        ids.toArray());
  }
}
