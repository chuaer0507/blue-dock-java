package com.bluedock.search.repo;

import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SearchQueryRepository {
  private final JdbcTemplate jdbc;

  public SearchQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SearchHitView> contacts(String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT id, nickname, email FROM bluedock_users
        WHERE disable_at IS NULL AND is_bot = 0
          AND (nickname LIKE ? OR email LIKE ?)
        ORDER BY id ASC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                "contact",
                rs.getLong("id"),
                nullToEmpty(rs.getString("nickname")),
                nullToEmpty(rs.getString("email")),
                0L),
        like,
        like,
        limit);
  }

  public List<SearchHitView> projects(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT p.id, p.name, p.description
        FROM bluedock_projects p
        INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
        WHERE p.deleted_at IS NULL AND p.archived_at IS NULL
          AND (p.name LIKE ? OR IFNULL(p.description,'') LIKE ?)
        ORDER BY p.id DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                "project",
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                nullToEmpty(rs.getString("description")),
                rs.getLong("id")),
        userId,
        like,
        like,
        limit);
  }

  public List<SearchHitView> tasks(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT t.id, t.name, t.description, t.project_id
        FROM bluedock_tasks t
        INNER JOIN bluedock_project_users pu ON pu.project_id = t.project_id AND pu.user_id = ?
        WHERE t.deleted_at IS NULL AND t.archived_at IS NULL AND t.parent_id = 0
          AND (t.name LIKE ? OR IFNULL(t.description,'') LIKE ?)
        ORDER BY t.id DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                "task",
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                nullToEmpty(rs.getString("description")),
                rs.getLong("project_id")),
        userId,
        like,
        like,
        limit);
  }

  public List<SearchHitView> files(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT id, name, type FROM bluedock_files
        WHERE user_id = ? AND deleted_at IS NULL AND name LIKE ?
        ORDER BY id DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                "file",
                rs.getLong("id"),
                nullToEmpty(rs.getString("name")),
                nullToEmpty(rs.getString("type")),
                0L),
        userId,
        like,
        limit);
  }

  public List<SearchHitView> messages(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT m.id, m.body, m.dialog_id
        FROM bluedock_dialog_messages m
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = m.dialog_id AND du.user_id = ?
        INNER JOIN bluedock_dialogs d ON d.id = m.dialog_id AND d.deleted_at IS NULL
        WHERE m.deleted_at IS NULL AND m.type = 'text' AND m.body LIKE ?
        ORDER BY m.id DESC
        LIMIT ?
        """,
        (rs, i) -> {
          String body = nullToEmpty(rs.getString("body"));
          String snippet = body.length() > 120 ? body.substring(0, 120) : body;
          return new SearchHitView("message", rs.getLong("id"), snippet, snippet, 0L);
        },
        userId,
        like,
        limit);
  }

  private static String escape(String key) {
    return key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
