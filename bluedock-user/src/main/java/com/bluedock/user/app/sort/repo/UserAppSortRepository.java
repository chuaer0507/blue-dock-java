package com.bluedock.user.app.sort.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAppSortRepository {
  private final JdbcTemplate jdbc;

  public UserAppSortRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> findSortsJson(long userId) {
    var list =
        jdbc.query(
            "SELECT sorts FROM bluedock_user_app_sorts WHERE user_id = ? LIMIT 1",
            (rs, i) -> rs.getString(1),
            userId);
    return list.stream().findFirst();
  }

  public void upsert(long userId, String sortsJson) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_user_app_sorts SET sorts = ?, updated_at = ? WHERE user_id = ?
            """,
            sortsJson,
            Timestamp.valueOf(now),
            userId);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_user_app_sorts (id, user_id, sorts, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          sortsJson,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }
}
