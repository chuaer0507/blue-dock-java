package com.bluedock.worker.notify.repo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DialogMuteCheckRepository {
  private final JdbcTemplate jdbc;

  public DialogMuteCheckRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 会话内免打扰用户集合。 */
  public Set<Long> mutedUserIds(long dialogId, Collection<Long> userIds) {
    if (dialogId <= 0 || userIds == null || userIds.isEmpty()) {
      return Set.of();
    }
    List<Long> ids = new ArrayList<>();
    for (Long id : userIds) {
      if (id != null && id > 0) {
        ids.add(id);
      }
    }
    if (ids.isEmpty()) {
      return Set.of();
    }
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Object> args = new ArrayList<>(ids.size() + 1);
    args.add(dialogId);
    args.addAll(ids);
    Set<Long> muted = new HashSet<>();
    jdbc.query(
        """
        SELECT user_id FROM bluedock_dialog_users
        WHERE dialog_id = ? AND user_id IN (%s) AND IFNULL(is_muted, 0) = 1
        """
            .formatted(placeholders),
        rs -> {
          muted.add(rs.getLong("user_id"));
        },
        args.toArray());
    return muted;
  }
}
