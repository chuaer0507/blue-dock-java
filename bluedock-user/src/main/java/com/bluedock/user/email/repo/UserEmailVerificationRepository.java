package com.bluedock.user.email.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserEmailVerificationRepository {
  private final JdbcTemplate jdbc;

  public UserEmailVerificationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void deleteByUserId(long userId) {
    jdbc.update("DELETE FROM bluedock_user_email_verifications WHERE user_id = ?", userId);
  }

  public Optional<Map<String, Object>> findRecentPending(long userId, LocalDateTime after) {
    List<Map<String, Object>> list =
        jdbc.queryForList(
            """
            SELECT id, user_id AS userId, code, email, type, status, created_at AS createdAt
            FROM bluedock_user_email_verifications
            WHERE user_id = ? AND status = 0 AND created_at > ?
            ORDER BY id DESC LIMIT 1
            """,
            userId,
            Timestamp.valueOf(after));
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findByCode(String code) {
    List<Map<String, Object>> list =
        jdbc.queryForList(
            """
            SELECT id, user_id AS userId, code, email, type, status, created_at AS createdAt
            FROM bluedock_user_email_verifications
            WHERE code = ?
            LIMIT 1
            """,
            code);
    return list.stream().findFirst();
  }

  public long insert(long userId, String email, String code, String type) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_email_verifications
          (id, user_id, code, email, type, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        userId,
        code,
        email,
        type,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return id;
  }

  public void markUsed(long id) {
    jdbc.update(
        """
        UPDATE bluedock_user_email_verifications
        SET status = 1, updated_at = ?
        WHERE id = ?
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }
}
