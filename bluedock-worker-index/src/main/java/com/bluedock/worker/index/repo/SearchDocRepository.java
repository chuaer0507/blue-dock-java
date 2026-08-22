package com.bluedock.worker.index.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SearchDocRepository {
  private final JdbcTemplate jdbc;

  public SearchDocRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(
      String docType,
      long refId,
      long userId,
      long projectId,
      String title,
      String content,
      String eventId) {
    LocalDateTime now = LocalDateTime.now();
    int updated =
        jdbc.update(
            """
            UPDATE bluedock_search_docs
            SET user_id = ?, project_id = ?, title = ?, content = ?, event_id = ?, updated_at = ?
            WHERE doc_type = ? AND ref_id = ?
            """,
            userId,
            projectId,
            title == null ? "" : title,
            content,
            eventId,
            Timestamp.valueOf(now),
            docType,
            refId);
    if (updated == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_search_docs
            (id, doc_type, ref_id, user_id, project_id, title, content, event_id, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          docType,
          refId,
          userId,
          projectId,
          title == null ? "" : title,
          content,
          eventId,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public void delete(String docType, long refId) {
    jdbc.update("DELETE FROM bluedock_search_docs WHERE doc_type = ? AND ref_id = ?", docType, refId);
  }

  public void deleteByType(String docType) {
    jdbc.update("DELETE FROM bluedock_search_docs WHERE doc_type = ?", docType);
  }
}
