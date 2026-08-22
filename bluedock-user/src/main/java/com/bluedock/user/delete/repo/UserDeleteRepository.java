package com.bluedock.user.delete.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserDeleteRepository {
  private final JdbcTemplate jdbc;

  public UserDeleteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long insert(long userId, String email, String nickname, String reason, String cacheJson) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_deletes
          (id, user_id, email, nickname, reason, cache, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        userId,
        email == null ? "" : email,
        nickname == null ? "" : nickname,
        reason == null ? "" : reason,
        cacheJson,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return id;
  }
}
