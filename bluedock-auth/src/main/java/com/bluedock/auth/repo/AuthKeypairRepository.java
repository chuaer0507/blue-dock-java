package com.bluedock.auth.repo;

import com.bluedock.auth.domain.AuthKeypair;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuthKeypairRepository {
  private static final RowMapper<AuthKeypair> MAPPER = AuthKeypairRepository::mapRow;

  private final JdbcTemplate jdbc;

  public AuthKeypairRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AuthKeypair> findActive() {
    List<AuthKeypair> list =
        jdbc.query(
            """
            SELECT id, key_id, public_key, private_key_enc, algorithm, status, created_at, expired_at
            FROM bluedock_auth_key_pairs
            WHERE status = 'active'
            ORDER BY created_at DESC
            LIMIT 1
            """,
            MAPPER);
    return list.stream().findFirst();
  }

  public void insert(AuthKeypair entity) {
    jdbc.update(
        """
        INSERT INTO bluedock_auth_key_pairs
          (id, key_id, public_key, private_key_enc, algorithm, status, created_at, expired_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        entity.getId(),
        entity.getKeyId(),
        entity.getPublicKey(),
        entity.getPrivateKeyEnc(),
        entity.getAlgorithm(),
        entity.getStatus(),
        Timestamp.valueOf(entity.getCreatedAt()),
        entity.getExpiredAt() == null ? null : Timestamp.valueOf(entity.getExpiredAt()));
  }

  private static AuthKeypair mapRow(ResultSet rs, int rowNum) throws SQLException {
    AuthKeypair e = new AuthKeypair();
    e.setId(rs.getString("id"));
    e.setKeyId(rs.getString("key_id"));
    e.setPublicKey(rs.getString("public_key"));
    e.setPrivateKeyEnc(rs.getString("private_key_enc"));
    e.setAlgorithm(rs.getString("algorithm"));
    e.setStatus(rs.getString("status"));
    Timestamp created = rs.getTimestamp("created_at");
    e.setCreatedAt(created == null ? null : created.toLocalDateTime());
    Timestamp expired = rs.getTimestamp("expired_at");
    e.setExpiredAt(expired == null ? null : expired.toLocalDateTime());
    return e;
  }
}
