package com.bluedock.assistant.repo;

import com.bluedock.assistant.domain.AssistantSession;
import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AssistantSessionRepository {
  private static final RowMapper<AssistantSession> MAPPER =
      (rs, i) -> {
        AssistantSession s = new AssistantSession();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getLong("user_id"));
        s.setSessionKey(rs.getString("session_key"));
        s.setSessionId(rs.getString("session_id"));
        s.setSceneKey(rs.getString("scene_key"));
        s.setTitle(rs.getString("title"));
        s.setDataJson(rs.getString("data"));
        s.setImagesJson(rs.getString("images"));
        Timestamp c = rs.getTimestamp("created_at");
        Timestamp u = rs.getTimestamp("updated_at");
        if (c != null) {
          s.setCreatedAt(c.toLocalDateTime());
        }
        if (u != null) {
          s.setUpdatedAt(u.toLocalDateTime());
        }
        return s;
      };

  private final JdbcTemplate jdbc;

  public AssistantSessionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AssistantSession> listByUserAndKey(long userId, String sessionKey) {
    return jdbc.query(
        """
        SELECT * FROM bluedock_ai_assistant_sessions
        WHERE user_id = ? AND session_key = ?
        ORDER BY updated_at DESC
        """,
        MAPPER,
        userId,
        sessionKey);
  }

  public Optional<AssistantSession> find(long userId, String sessionKey, String sessionId) {
    var list =
        jdbc.query(
            """
            SELECT * FROM bluedock_ai_assistant_sessions
            WHERE user_id = ? AND session_key = ? AND session_id = ?
            LIMIT 1
            """,
            MAPPER,
            userId,
            sessionKey,
            sessionId);
    return list.stream().findFirst();
  }

  public void upsert(AssistantSession s) {
    LocalDateTime now = LocalDateTime.now();
    Optional<AssistantSession> exist = find(s.getUserId(), s.getSessionKey(), s.getSessionId());
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_ai_assistant_sessions
          SET scene_key = ?, title = ?, data = ?, images = ?, updated_at = ?
          WHERE id = ?
          """,
          s.getSceneKey(),
          s.getTitle(),
          s.getDataJson(),
          s.getImagesJson(),
          Timestamp.valueOf(now),
          exist.get().getId());
      s.setId(exist.get().getId());
      return;
    }
    long id = IdGenerator.nextId();
    jdbc.update(
        """
        INSERT INTO bluedock_ai_assistant_sessions
          (id, user_id, session_key, session_id, scene_key, title, data, images, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        s.getUserId(),
        s.getSessionKey(),
        s.getSessionId(),
        s.getSceneKey(),
        s.getTitle(),
        s.getDataJson(),
        s.getImagesJson(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    s.setId(id);
  }

  public int deleteOne(long userId, String sessionKey, String sessionId) {
    return jdbc.update(
        """
        DELETE FROM bluedock_ai_assistant_sessions
        WHERE user_id = ? AND session_key = ? AND session_id = ?
        """,
        userId,
        sessionKey,
        sessionId);
  }

  public int deleteAll(long userId, String sessionKey) {
    return jdbc.update(
        """
        DELETE FROM bluedock_ai_assistant_sessions
        WHERE user_id = ? AND session_key = ?
        """,
        userId,
        sessionKey);
  }
}
