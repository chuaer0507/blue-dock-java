package com.bluedock.user.apppush.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppPushAliasRepository {
  private final JdbcTemplate jdbc;

  public AppPushAliasRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Long> findId(long userId, String alias, String platform) {
    var list =
        jdbc.query(
            """
            SELECT id FROM bluedock_user_push_aliases
            WHERE user_id = ? AND alias = ? AND platform = ?
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userId,
            alias,
            platform);
    return list.stream().findFirst();
  }

  public void deleteByAlias(String alias) {
    jdbc.update("DELETE FROM bluedock_user_push_aliases WHERE alias = ?", alias);
  }

  public void upsert(
      long userId,
      String alias,
      String platform,
      String userAgent,
      String device,
      String deviceHash,
      String version,
      boolean notified) {
    LocalDateTime now = LocalDateTime.now();
    Optional<Long> exist = findId(userId, alias, platform);
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_user_push_aliases
          SET user_agent = ?, device = ?, device_hash = ?, version = ?, is_notified = ?, updated_at = ?
          WHERE id = ?
          """,
          userAgent,
          device,
          deviceHash,
          version,
          notified ? 1 : 0,
          Timestamp.valueOf(now),
          exist.get());
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_user_push_aliases
          (id, user_id, alias, platform, user_agent, device, device_hash, version, is_notified, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        userId,
        alias,
        platform,
        userAgent,
        device,
        deviceHash,
        version,
        notified ? 1 : 0,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }
}
