package com.bluedock.user.attendance.repo;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceRemindRepository {
  private final JdbcTemplate jdbc;

  public AttendanceRemindRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 在职非机器人、今日未打卡、且过去 {@code lookbackDays} 天内有过签到记录的用户。
   */
  public List<Long> listRemindCandidates(LocalDate today, int lookbackDays) {
    LocalDate from = today.minusDays(lookbackDays);
    return jdbc.query(
        """
        SELECT u.id
        FROM bluedock_users u
        WHERE u.disable_at IS NULL
          AND IFNULL(u.is_bot, 0) = 0
          AND NOT EXISTS (
            SELECT 1 FROM bluedock_user_attendance_records t
            WHERE t.user_id = u.id AND t.attendance_date = ?
          )
          AND EXISTS (
            SELECT 1 FROM bluedock_user_attendance_records r
            WHERE r.user_id = u.id
              AND r.attendance_date >= ?
              AND r.attendance_date < ?
          )
        ORDER BY u.id
        """,
        (rs, i) -> rs.getLong(1),
        Date.valueOf(today),
        Date.valueOf(from),
        Date.valueOf(today));
  }
}
