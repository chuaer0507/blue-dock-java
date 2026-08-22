package com.bluedock.system.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingRepository {
  private final JdbcTemplate jdbc;

  public SettingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> findSettingJson(String name) {
    var list =
        jdbc.query(
            "SELECT setting FROM bluedock_settings WHERE name = ?",
            (rs, i) -> rs.getString(1),
            name);
    return list.stream().findFirst();
  }

  public void upsert(String name, String json) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_settings SET setting = ?, updated_at = ? WHERE name = ?
            """,
            json,
            Timestamp.valueOf(now),
            name);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_settings (id, name, description, setting, created_at, updated_at)
          VALUES (?, ?, '', ?, ?, ?)
          """,
          IdGenerator.nextId(),
          name,
          json,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }
}
