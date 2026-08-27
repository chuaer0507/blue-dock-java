package com.bluedock.worker.notify.repo;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotifySettingRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public NotifySettingRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> load(String name, Map<String, Object> defaults) {
    Optional<String> json =
        jdbc
            .query(
                "SELECT setting FROM bluedock_settings WHERE name = ?",
                (rs, i) -> rs.getString(1),
                name)
            .stream()
            .findFirst();
    if (json.isEmpty()) {
      return new LinkedHashMap<>(defaults);
    }
    try {
      Map<String, Object> m =
          objectMapper.readValue(json.get(), new TypeReference<Map<String, Object>>() {});
      Map<String, Object> out = new LinkedHashMap<>(defaults);
      out.putAll(m);
      return out;
    } catch (Exception e) {
      return new LinkedHashMap<>(defaults);
    }
  }
}
