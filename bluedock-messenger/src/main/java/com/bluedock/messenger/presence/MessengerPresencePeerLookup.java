package com.bluedock.messenger.presence;

import com.bluedock.common.realtime.PresencePeerLookup;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 与用户共享任一未删除会话的对端（上限 200）。 */
@Component
public class MessengerPresencePeerLookup implements PresencePeerLookup {
  private static final int LIMIT = 200;

  private final JdbcTemplate jdbc;

  public MessengerPresencePeerLookup(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Long> peerUserIds(long userId) {
    if (userId <= 0) {
      return List.of();
    }
    List<Long> rows =
        jdbc.query(
            """
            SELECT DISTINCT du2.user_id AS peerId
            FROM bluedock_dialog_users du1
            INNER JOIN bluedock_dialogs d
              ON d.id = du1.dialog_id AND d.deleted_at IS NULL
            INNER JOIN bluedock_dialog_users du2
              ON du2.dialog_id = du1.dialog_id AND du2.user_id <> du1.user_id
            INNER JOIN bluedock_users u
              ON u.id = du2.user_id
             AND IFNULL(u.is_bot, 0) = 0
             AND u.disable_at IS NULL
            WHERE du1.user_id = ?
            ORDER BY peerId ASC
            LIMIT ?
            """,
            (rs, i) -> rs.getLong("peerId"),
            userId,
            LIMIT);
    return rows == null ? List.of() : new ArrayList<>(rows);
  }
}
