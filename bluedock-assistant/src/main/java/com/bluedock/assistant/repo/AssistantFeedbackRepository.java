package com.bluedock.assistant.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantFeedbackRepository {
  private final JdbcTemplate jdbc;

  public AssistantFeedbackRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Long> findId(long userId, String sessionKey, String sessionId, long localId) {
    var list =
        jdbc.query(
            """
            SELECT id FROM bluedock_ai_assistant_feedbacks
            WHERE user_id = ? AND session_key = ? AND session_id = ? AND local_id = ?
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userId,
            sessionKey,
            sessionId,
            localId);
    return list.stream().findFirst();
  }

  public void delete(long userId, String sessionKey, String sessionId, long localId) {
    jdbc.update(
        """
        DELETE FROM bluedock_ai_assistant_feedbacks
        WHERE user_id = ? AND session_key = ? AND session_id = ? AND local_id = ?
        """,
        userId,
        sessionKey,
        sessionId,
        localId);
  }

  public void upsert(
      long userId,
      String sessionKey,
      String sessionId,
      long localId,
      String feedback,
      String prompt,
      String answer,
      String answerDigest,
      String sourceIdsJson,
      String model) {
    LocalDateTime now = LocalDateTime.now();
    Optional<Long> exist = findId(userId, sessionKey, sessionId, localId);
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_ai_assistant_feedbacks
          SET feedback = ?, prompt = ?, answer = ?, answer_digest = ?,
              source_ids = ?, model = ?, updated_at = ?
          WHERE id = ?
          """,
          feedback,
          prompt,
          answer,
          answerDigest,
          sourceIdsJson,
          model,
          Timestamp.valueOf(now),
          exist.get());
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_ai_assistant_feedbacks
          (id, user_id, session_key, session_id, local_id, feedback, prompt, answer,
           answer_digest, source_ids, model, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        userId,
        sessionKey,
        sessionId,
        localId,
        feedback,
        prompt,
        answer,
        answerDigest,
        sourceIdsJson,
        model,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }
}
