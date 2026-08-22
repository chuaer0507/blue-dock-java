package com.bluedock.assistant.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantSearchLogRepository {
  private final JdbcTemplate jdbc;

  public AssistantSearchLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      long userId,
      long dialogId,
      String contextKey,
      String source,
      String query,
      String locale,
      String sourceIdsJson,
      double topScore,
      int resultCount,
      int durationMs) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_ai_assistant_search_logs
          (id, user_id, dialog_id, context_key, source, query_text, locale, source_ids,
           top_score, result_count, duration_ms, empty_hit, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        userId,
        dialogId,
        contextKey,
        source,
        query,
        locale,
        sourceIdsJson,
        topScore,
        resultCount,
        durationMs,
        resultCount > 0 ? 0 : 1,
        Timestamp.valueOf(now));
  }
}
