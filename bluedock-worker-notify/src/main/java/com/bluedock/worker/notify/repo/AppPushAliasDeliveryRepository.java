package com.bluedock.worker.notify.repo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppPushAliasDeliveryRepository {
  private final JdbcTemplate jdbc;

  public AppPushAliasDeliveryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 30 天内活跃别名；每用户每平台最多 5 条（按 updated_at 倒序，应用侧再截断）。
   */
  public List<AppPushAliasRow> listActive(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>(userIds);
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    LocalDateTime since = LocalDateTime.now().minusDays(30);
    List<Object> args = new ArrayList<>(ids.size() + 1);
    args.addAll(ids);
    args.add(Timestamp.valueOf(since));
    return jdbc.query(
        """
        SELECT user_id, alias, platform FROM bluedock_user_push_aliases
        WHERE user_id IN (%s)
          AND updated_at >= ?
          AND platform IN ('ios','android')
          AND IFNULL(is_notified,1) = 1
        ORDER BY user_id, platform, updated_at DESC
        """
            .formatted(placeholders),
        (rs, i) ->
            new AppPushAliasRow(
                rs.getLong("user_id"), rs.getString("alias"), rs.getString("platform")),
        args.toArray());
  }
}
