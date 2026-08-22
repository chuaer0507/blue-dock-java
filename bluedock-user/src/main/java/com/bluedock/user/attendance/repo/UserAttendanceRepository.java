package com.bluedock.user.attendance.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAttendanceRepository {
  private final JdbcTemplate jdbc;

  public UserAttendanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> listMacAddresses(long userId) {
    return jdbc.query(
        "SELECT mac_address FROM bluedock_user_attendance_macs WHERE user_id = ? ORDER BY id ASC",
        (rs, i) -> rs.getString(1),
        userId);
  }

  public void replaceMacAddresses(long userId, List<String> macAddresses) {
    jdbc.update("DELETE FROM bluedock_user_attendance_macs WHERE user_id = ?", userId);
    LocalDateTime now = LocalDateTime.now();
    for (String macAddress : macAddresses) {
      jdbc.update(
          """
          INSERT INTO bluedock_user_attendance_macs (id, user_id, mac_address, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          macAddress,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public Optional<Long> findUserIdByMacAddress(String macAddress) {
    var list =
        jdbc.query(
            "SELECT user_id FROM bluedock_user_attendance_macs WHERE mac_address = ? LIMIT 1",
            (rs, i) -> rs.getLong(1),
            macAddress);
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findRecord(long userId, LocalDate date) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, attendance_date, times, created_at, updated_at
            FROM bluedock_user_attendance_records
            WHERE user_id = ? AND attendance_date = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("userId", rs.getLong("user_id"));
              m.put("attendanceDate", rs.getDate("attendance_date").toLocalDate().toString());
              m.put("times", rs.getString("times") == null ? "[]" : rs.getString("times"));
              return m;
            },
            userId,
            Date.valueOf(date));
    return list.stream().findFirst();
  }

  public List<Map<String, Object>> listRecords(long userId, LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        SELECT id, user_id, attendance_date, times, created_at, updated_at
        FROM bluedock_user_attendance_records
        WHERE user_id = ? AND attendance_date >= ? AND attendance_date <= ?
        ORDER BY attendance_date ASC
        """,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          m.put("userId", rs.getLong("user_id"));
          m.put("attendanceDate", rs.getDate("attendance_date").toLocalDate().toString());
          m.put("times", rs.getString("times") == null ? "[]" : rs.getString("times"));
          return m;
        },
        userId,
        Date.valueOf(from),
        Date.valueOf(to));
  }

  public void upsertRecord(long userId, LocalDate date, String timesJson) {
    LocalDateTime now = LocalDateTime.now();
    Optional<Map<String, Object>> exist = findRecord(userId, date);
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_user_attendance_records SET times = ?, updated_at = ?
          WHERE user_id = ? AND attendance_date = ?
          """,
          timesJson,
          Timestamp.valueOf(now),
          userId,
          Date.valueOf(date));
    } else {
      jdbc.update(
          """
          INSERT INTO bluedock_user_attendance_records
            (id, user_id, attendance_date, times, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          Date.valueOf(date),
          timesJson,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public Optional<Long> findFaceUploadObjectId(long userId) {
    var list =
        jdbc.query(
            """
            SELECT upload_object_id FROM bluedock_user_attendance_faces
            WHERE user_id = ? LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userId);
    return list.stream().filter(id -> id != null && id > 0).findFirst();
  }

  public boolean hasFace(long userId) {
    return findFaceUploadObjectId(userId).isPresent();
  }

  public void upsertFace(long userId, long uploadObjectId) {
    LocalDateTime now = LocalDateTime.now();
    Optional<Long> exist = findFaceUploadObjectId(userId);
    if (exist.isPresent()) {
      jdbc.update(
          """
          UPDATE bluedock_user_attendance_faces SET upload_object_id = ?, updated_at = ?
          WHERE user_id = ?
          """,
          uploadObjectId,
          Timestamp.valueOf(now),
          userId);
    } else {
      jdbc.update(
          """
          INSERT INTO bluedock_user_attendance_faces
            (id, user_id, upload_object_id, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          uploadObjectId,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public boolean uploadObjectExists(long uploadObjectId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_upload_objects
            WHERE id = ? AND deleted_at IS NULL
            """,
            Integer.class,
            uploadObjectId);
    return n != null && n > 0;
  }
}
