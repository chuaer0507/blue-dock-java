package com.bluedock.system.apps.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.apps.domain.InstalledApp;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InstalledAppRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final RowMapper<InstalledApp> mapper;

  public InstalledAppRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.mapper =
        (rs, i) -> {
          InstalledApp a = new InstalledApp();
          a.setId(rs.getLong("id"));
          a.setAppId(rs.getString("app_id"));
          a.setName(rs.getString("name"));
          a.setSecret(rs.getString("secret"));
          a.setStatus(rs.getString("status"));
          a.setVersion(rs.getString("version"));
          a.setMenus(parseMenus(rs.getString("menus")));
          Timestamp c = rs.getTimestamp("created_at");
          Timestamp u = rs.getTimestamp("updated_at");
          if (c != null) {
            a.setCreatedAt(c.toLocalDateTime());
          }
          if (u != null) {
            a.setUpdatedAt(u.toLocalDateTime());
          }
          return a;
        };
  }

  public Optional<InstalledApp> findInstalled(String appId) {
    var list =
        jdbc.query(
            """
            SELECT * FROM bluedock_installed_apps
            WHERE app_id = ? AND status = 'installed'
            LIMIT 1
            """,
            mapper,
            appId);
    return list.stream().findFirst();
  }

  public List<InstalledApp> listInstalled() {
    return jdbc.query(
        "SELECT * FROM bluedock_installed_apps WHERE status = 'installed' ORDER BY app_id", mapper);
  }

  public void upsert(InstalledApp app) {
    LocalDateTime now = LocalDateTime.now();
    String menusJson;
    try {
      menusJson = objectMapper.writeValueAsString(app.getMenus() == null ? List.of() : app.getMenus());
    } catch (Exception e) {
      menusJson = "[]";
    }
    Optional<InstalledApp> exist = findAny(app.getAppId());
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_installed_apps
          SET name = ?, secret = ?, status = ?, version = ?, menus = ?, updated_at = ?
          WHERE id = ?
          """,
          app.getName(),
          app.getSecret(),
          app.getStatus(),
          blankVersion(app.getVersion()),
          menusJson,
          Timestamp.valueOf(now),
          exist.get().getId());
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_installed_apps
          (id, app_id, name, secret, status, version, menus, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        app.getAppId(),
        app.getName(),
        app.getSecret(),
        app.getStatus(),
        blankVersion(app.getVersion()),
        menusJson,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void markUninstalled(String appId) {
    jdbc.update(
        """
        UPDATE bluedock_installed_apps SET status = 'uninstalled', updated_at = ?
        WHERE app_id = ?
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        appId);
  }

  private Optional<InstalledApp> findAny(String appId) {
    var list =
        jdbc.query("SELECT * FROM bluedock_installed_apps WHERE app_id = ? LIMIT 1", mapper, appId);
    return list.stream().findFirst();
  }

  private List<Map<String, Object>> parseMenus(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private static String blankVersion(String version) {
    return version == null || version.isBlank() ? "1.0.0" : version.trim();
  }
}
