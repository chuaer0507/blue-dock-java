package com.bluedock.system.apps.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppBadgeRepository {
  private final JdbcTemplate jdbc;

  public AppBadgeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(String appId, String menuKey, long userId, int count, boolean dot) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_app_badges
            SET badge_count = ?, badge_dot = ?, updated_at = ?
            WHERE app_id = ? AND menu_key = ? AND user_id = ?
            """,
            count,
            dot ? 1 : 0,
            Timestamp.valueOf(now),
            appId,
            menuKey,
            userId);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_app_badges
            (id, app_id, menu_key, user_id, badge_count, badge_dot, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          appId,
          menuKey,
          userId,
          count,
          dot ? 1 : 0,
          Timestamp.valueOf(now));
    }
  }

  public void delete(String appId, String menuKey, long userId) {
    jdbc.update(
        """
        DELETE FROM bluedock_app_badges
        WHERE app_id = ? AND menu_key = ? AND user_id = ?
        """,
        appId,
        menuKey,
        userId);
  }

  public void deleteByApp(String appId) {
    jdbc.update("DELETE FROM bluedock_app_badges WHERE app_id = ?", appId);
  }

  public List<Map<String, Object>> listByUser(long userId) {
    return jdbc.query(
        """
        SELECT app_id, menu_key, badge_count, badge_dot
        FROM bluedock_app_badges WHERE user_id = ?
        """,
        (rs, i) ->
            Map.of(
                "appId", rs.getString("app_id"),
                "menuKey", rs.getString("menu_key") == null ? "" : rs.getString("menu_key"),
                "count", rs.getInt("badge_count"),
                "dot", rs.getInt("badge_dot") == 1),
        userId);
  }
}
