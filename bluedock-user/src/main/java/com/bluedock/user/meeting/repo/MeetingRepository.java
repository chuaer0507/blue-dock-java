package com.bluedock.user.meeting.repo;

import com.bluedock.user.meeting.domain.Meeting;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepository {
  private static final RowMapper<Meeting> MAPPER = MeetingRepository::mapRow;

  private final JdbcTemplate jdbc;

  public MeetingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Meeting m) {
    jdbc.update(
        """
        INSERT INTO bluedock_meetings
          (id, meeting_id, name, channel, user_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        m.getId(),
        m.getMeetingId(),
        m.getName(),
        m.getChannel(),
        m.getUserId(),
        Timestamp.valueOf(m.getCreatedAt()),
        Timestamp.valueOf(m.getUpdatedAt()));
  }

  public Optional<Meeting> findByMeetingId(String meetingId) {
    var list =
        jdbc.query(
            """
            SELECT id, meeting_id, name, channel, user_id, created_at, updated_at, end_at
            FROM bluedock_meetings
            WHERE meeting_id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            meetingId);
    return list.stream().findFirst();
  }

  public void touch(String meetingId) {
    jdbc.update(
        """
        UPDATE bluedock_meetings SET updated_at = ?
        WHERE meeting_id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        meetingId);
  }

  public void markEnded(String meetingId, LocalDateTime endAt) {
    jdbc.update(
        """
        UPDATE bluedock_meetings SET end_at = ?, updated_at = ?
        WHERE meeting_id = ? AND deleted_at IS NULL AND end_at IS NULL
        """,
        Timestamp.valueOf(endAt),
        Timestamp.valueOf(endAt),
        meetingId);
  }

  public List<Meeting> listStaleOpen(LocalDateTime updatedBefore, int limit) {
    return jdbc.query(
        """
        SELECT id, meeting_id, name, channel, user_id, created_at, updated_at, end_at
        FROM bluedock_meetings
        WHERE deleted_at IS NULL AND end_at IS NULL AND updated_at < ?
        ORDER BY updated_at ASC
        LIMIT ?
        """,
        MAPPER,
        Timestamp.valueOf(updatedBefore),
        limit);
  }

  private static Meeting mapRow(ResultSet rs, int rowNum) throws SQLException {
    Meeting m = new Meeting();
    m.setId(rs.getLong("id"));
    m.setMeetingId(rs.getString("meeting_id"));
    m.setName(rs.getString("name"));
    m.setChannel(rs.getString("channel"));
    m.setUserId(rs.getLong("user_id"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    Timestamp e = rs.getTimestamp("end_at");
    if (c != null) {
      m.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      m.setUpdatedAt(u.toLocalDateTime());
    }
    if (e != null) {
      m.setEndAt(e.toLocalDateTime());
    }
    return m;
  }
}
