package com.bluedock.search.repo;

import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 读 {@code bluedock_search_docs}（worker 增量写入），再按权限过滤。 */
@Repository
public class SearchDocsRepository {
  private final JdbcTemplate jdbc;

  public SearchDocsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SearchHitView> contacts(String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT d.ref_id, d.title, d.content
        FROM bluedock_search_docs d
        INNER JOIN bluedock_users u ON u.id = d.ref_id
          AND u.disable_at IS NULL AND IFNULL(u.is_bot,0) = 0
        WHERE d.doc_type = ?
          AND (d.title LIKE ? OR IFNULL(d.content,'') LIKE ?)
        ORDER BY d.updated_at DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                SearchIndexEvent.TYPE_CONTACT,
                rs.getLong("ref_id"),
                nullToEmpty(rs.getString("title")),
                nullToEmpty(rs.getString("content")),
                0L),
        SearchIndexEvent.TYPE_CONTACT,
        like,
        like,
        limit);
  }

  public List<SearchHitView> projects(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT d.ref_id, d.title, d.content
        FROM bluedock_search_docs d
        INNER JOIN bluedock_project_users pu ON pu.project_id = d.ref_id AND pu.user_id = ?
        INNER JOIN bluedock_projects p ON p.id = d.ref_id
          AND p.deleted_at IS NULL AND p.archived_at IS NULL
        WHERE d.doc_type = ?
          AND (d.title LIKE ? OR IFNULL(d.content,'') LIKE ?)
        ORDER BY d.updated_at DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                SearchIndexEvent.TYPE_PROJECT,
                rs.getLong("ref_id"),
                nullToEmpty(rs.getString("title")),
                nullToEmpty(rs.getString("content")),
                rs.getLong("ref_id")),
        userId,
        SearchIndexEvent.TYPE_PROJECT,
        like,
        like,
        limit);
  }

  public List<SearchHitView> tasks(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT d.ref_id, d.title, d.content, d.project_id
        FROM bluedock_search_docs d
        INNER JOIN bluedock_project_users pu ON pu.project_id = d.project_id AND pu.user_id = ?
        INNER JOIN bluedock_tasks t ON t.id = d.ref_id
          AND t.deleted_at IS NULL AND t.archived_at IS NULL AND t.parent_id = 0
        WHERE d.doc_type = ?
          AND (d.title LIKE ? OR IFNULL(d.content,'') LIKE ?)
        ORDER BY d.updated_at DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                SearchIndexEvent.TYPE_TASK,
                rs.getLong("ref_id"),
                nullToEmpty(rs.getString("title")),
                nullToEmpty(rs.getString("content")),
                rs.getLong("project_id")),
        userId,
        SearchIndexEvent.TYPE_TASK,
        like,
        like,
        limit);
  }

  public List<SearchHitView> messages(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT d.ref_id, d.title, d.content
        FROM bluedock_search_docs d
        INNER JOIN bluedock_dialog_messages m ON m.id = d.ref_id AND m.deleted_at IS NULL
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = m.dialog_id AND du.user_id = ?
        INNER JOIN bluedock_dialogs dg ON dg.id = m.dialog_id AND dg.deleted_at IS NULL
        WHERE d.doc_type = ?
          AND (d.title LIKE ? OR IFNULL(d.content,'') LIKE ?)
        ORDER BY d.updated_at DESC
        LIMIT ?
        """,
        (rs, i) -> {
          String content = nullToEmpty(rs.getString("content"));
          if (content.isEmpty()) {
            content = nullToEmpty(rs.getString("title"));
          }
          String snippet = content.length() > 120 ? content.substring(0, 120) : content;
          return new SearchHitView(
              SearchIndexEvent.TYPE_MESSAGE, rs.getLong("ref_id"), snippet, snippet, 0L);
        },
        userId,
        SearchIndexEvent.TYPE_MESSAGE,
        like,
        like,
        limit);
  }

  public List<SearchHitView> files(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT d.ref_id, d.title, d.content
        FROM bluedock_search_docs d
        WHERE d.doc_type = ? AND d.user_id = ?
          AND (d.title LIKE ? OR IFNULL(d.content,'') LIKE ?)
        ORDER BY d.updated_at DESC
        LIMIT ?
        """,
        (rs, i) ->
            new SearchHitView(
                SearchIndexEvent.TYPE_FILE,
                rs.getLong("ref_id"),
                nullToEmpty(rs.getString("title")),
                nullToEmpty(rs.getString("content")),
                0L),
        SearchIndexEvent.TYPE_FILE,
        userId,
        like,
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
