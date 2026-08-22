package com.bluedock.auth.repo;

import com.bluedock.auth.domain.UserAccount;
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
public class UserAccountRepository {
  private static final String PROFILE_COLS =
      """
      id, identity, name_az, email, nickname, user_img, profession, telephone, birthday, address,
      introduction, lang, password, is_bot, email_verify, disable_at
      """;

  private static final RowMapper<UserAccount> MAPPER = UserAccountRepository::mapRow;

  private final JdbcTemplate jdbc;

  public UserAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserAccount> findByEmail(String email) {
    var list =
        jdbc.query(
            "SELECT " + PROFILE_COLS + " FROM bluedock_users WHERE email = ?", MAPPER, email);
    return list.stream().findFirst();
  }

  public Optional<UserAccount> findByUserId(long userId) {
    var list =
        jdbc.query(
            "SELECT " + PROFILE_COLS + " FROM bluedock_users WHERE id = ?", MAPPER, userId);
    return list.stream().findFirst();
  }

  public boolean existsByUserId(long userId) {
    Integer n =
        jdbc.queryForObject("SELECT COUNT(1) FROM bluedock_users WHERE id = ?", Integer.class, userId);
    return n != null && n > 0;
  }

  public boolean existsTelephoneExcept(String telephone, long exceptUserId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_users
            WHERE telephone = ? AND telephone <> '' AND id <> ?
            """,
            Integer.class,
            telephone,
            exceptUserId);
    return n != null && n > 0;
  }

  public void insert(UserAccount user) {
    LocalDateTime now = LocalDateTime.now();
    // 未显式设置 mustChangePassword → 历史调用（管理员/seed/bot），默认改密标记 + 已验证邮箱
    Integer must = user.getMustChangePassword();
    int mustChange = must != null ? must : 1;
    int emailVerify = must != null ? user.getEmailVerify() : 1;
    jdbc.update(
        """
        INSERT INTO bluedock_users
          (id, identity, email, nickname, user_img, profession, password, is_bot,
           must_change_password, email_verify, login_count, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        user.getUserId(),
        user.getIdentity() == null ? "[]" : user.getIdentity(),
        user.getEmail(),
        user.getNickname(),
        user.getUserImage() == null ? "" : user.getUserImage(),
        user.getProfession() == null ? "" : user.getProfession(),
        user.getPassword(),
        user.getIsBot(),
        mustChange,
        emailVerify,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public boolean existsByEmail(String email) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_users WHERE email = ?", Integer.class, email);
    if (n != null && n > 0) {
      return true;
    }
    // 注销后 30 天内邮箱保护（bluedock_user_deletes）
    Integer d =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_user_deletes
            WHERE email = ? AND created_at > DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 DAY)
            """,
            Integer.class,
            email == null ? "" : email.trim().toLowerCase());
    return d != null && d > 0;
  }

  public void deleteByUserId(long userId) {
    jdbc.update("DELETE FROM bluedock_users WHERE id = ?", userId);
  }

  public int countForAdmin(String keyword, boolean includeBot) {
    StringBuilder sql =
        new StringBuilder("SELECT COUNT(1) FROM bluedock_users WHERE 1=1");
    var args = new java.util.ArrayList<Object>();
    appendAdminFilters(sql, args, keyword, includeBot);
    Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
    return n == null ? 0 : n;
  }

  public List<UserAccount> listForAdmin(String keyword, boolean includeBot, int limit, int offset) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT " + PROFILE_COLS + " FROM bluedock_users WHERE 1=1");
    var args = new java.util.ArrayList<Object>();
    appendAdminFilters(sql, args, keyword, includeBot);
    sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  /**
   * 会员搜索。
   *
   * @param disable 0=排除离职，1=含离职，2=仅离职
   * @param bot 0=排除机器人，1=含机器人，2=仅机器人
   * @param azSort {@code asc}/{@code desc} 按 az 排序；其它忽略
   */
  public int countSearch(
      String keyword,
      int disable,
      int isBot,
      Long projectId,
      Long noProjectId) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM bluedock_users WHERE 1=1");
    var args = new java.util.ArrayList<Object>();
    appendSearchFilters(sql, args, keyword, disable, isBot, projectId, noProjectId);
    Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
    return n == null ? 0 : n;
  }

  public List<UserAccount> search(
      String keyword,
      int disable,
      int isBot,
      Long projectId,
      Long noProjectId,
      String azSort,
      int limit,
      int offset) {
    StringBuilder sql =
        new StringBuilder("SELECT " + PROFILE_COLS + " FROM bluedock_users WHERE 1=1");
    var args = new java.util.ArrayList<Object>();
    appendSearchFilters(sql, args, keyword, disable, isBot, projectId, noProjectId);
    if ("asc".equalsIgnoreCase(azSort) || "desc".equalsIgnoreCase(azSort)) {
      sql.append(" ORDER BY name_az ").append(azSort.equalsIgnoreCase("desc") ? "DESC" : "ASC");
      sql.append(", id ASC");
    } else {
      sql.append(" ORDER BY id ASC");
    }
    sql.append(" LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  /** AI 系统机器人（email 前缀 {@code ai-}）。 */
  public List<UserAccount> listAiBots(int limit) {
    return jdbc.query(
        """
        SELECT %s FROM bluedock_users
        WHERE is_bot = 1 AND disable_at IS NULL
          AND email LIKE 'ai-%%@bot.system'
        ORDER BY email ASC
        LIMIT ?
        """
            .formatted(PROFILE_COLS),
        MAPPER,
        Math.max(1, Math.min(100, limit)));
  }

  private static void appendAdminFilters(
      StringBuilder sql, java.util.List<Object> args, String keyword, boolean includeBot) {
    if (!includeBot) {
      sql.append(" AND IFNULL(is_bot, 0) = 0");
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      sql.append(" AND (email LIKE ? OR nickname LIKE ? OR telephone LIKE ?)");
      args.add(like);
      args.add(like);
      args.add(like);
    }
  }

  private static void appendSearchFilters(
      StringBuilder sql,
      java.util.List<Object> args,
      String keyword,
      int disable,
      int isBot,
      Long projectId,
      Long noProjectId) {
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      sql.append(" AND (email LIKE ? OR nickname LIKE ?)");
      args.add(like);
      args.add(like);
    }
    if (disable == 0) {
      sql.append(" AND disable_at IS NULL");
    } else if (disable == 2) {
      sql.append(" AND disable_at IS NOT NULL");
    }
    if (isBot == 0) {
      sql.append(" AND IFNULL(is_bot, 0) = 0");
    } else if (isBot == 2) {
      sql.append(" AND is_bot = 1");
    }
    if (projectId != null && projectId > 0) {
      sql.append(
          """
           AND id IN (
            SELECT user_id FROM bluedock_project_users WHERE project_id = ?
          )
          """);
      args.add(projectId);
    }
    if (noProjectId != null && noProjectId > 0) {
      sql.append(
          """
           AND id NOT IN (
            SELECT user_id FROM bluedock_project_users WHERE project_id = ?
          )
          """);
      args.add(noProjectId);
    }
  }

  public void updateProfile(UserAccount user) {
    jdbc.update(
        """
        UPDATE bluedock_users
        SET nickname = ?, user_img = ?, profession = ?, telephone = ?, birthday = ?,
            address = ?, introduction = ?, lang = ?, updated_at = ?
        WHERE id = ?
        """,
        nullToEmpty(user.getNickname()),
        nullToEmpty(user.getUserImage()),
        nullToEmpty(user.getProfession()),
        nullToEmpty(user.getTelephone()),
        nullToEmpty(user.getBirthday()),
        nullToEmpty(user.getAddress()),
        nullToEmpty(user.getIntroduction()),
        nullToEmpty(user.getLang()),
        Timestamp.valueOf(LocalDateTime.now()),
        user.getUserId());
  }

  public void updateIdentity(long userId, String identity) {
    jdbc.update(
        """
        UPDATE bluedock_users SET identity = ?, updated_at = ?
        WHERE id = ?
        """,
        identity == null ? "[]" : identity,
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  public void updatePassword(long userId, String passwordHash) {
    jdbc.update(
        """
        UPDATE bluedock_users
        SET password = ?, must_change_password = 0, updated_at = ?
        WHERE id = ?
        """,
        passwordHash == null ? "" : passwordHash,
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  public void updateDisableAt(long userId, LocalDateTime disableAt) {
    jdbc.update(
        """
        UPDATE bluedock_users SET disable_at = ?, updated_at = ?
        WHERE id = ?
        """,
        disableAt == null ? null : Timestamp.valueOf(disableAt),
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  public void updateEmailVerify(long userId, int emailVerify) {
    jdbc.update(
        """
        UPDATE bluedock_users SET email_verify = ?, updated_at = ?
        WHERE id = ?
        """,
        emailVerify,
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  public void updateEmail(long userId, String email) {
    jdbc.update(
        """
        UPDATE bluedock_users SET email = ?, email_verify = 1, updated_at = ?
        WHERE id = ?
        """,
        email == null ? "" : email.trim().toLowerCase(),
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  public void touchLogin(long userId, String ip) {
    jdbc.update(
        """
        UPDATE bluedock_users
        SET login_count = COALESCE(login_count, 0) + 1,
            last_ip = ?,
            last_at = ?,
            updated_at = ?
        WHERE id = ?
        """,
        ip == null ? "" : ip,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        userId);
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static UserAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
    UserAccount u = new UserAccount();
    u.setUserId(rs.getLong("id"));
    u.setIdentity(rs.getString("identity"));
    u.setNameAz(rs.getString("name_az"));
    u.setEmail(rs.getString("email"));
    u.setNickname(rs.getString("nickname"));
    u.setUserImage(rs.getString("user_img"));
    u.setProfession(rs.getString("profession"));
    u.setTelephone(rs.getString("telephone"));
    u.setBirthday(rs.getString("birthday"));
    u.setAddress(rs.getString("address"));
    u.setIntroduction(rs.getString("introduction"));
    u.setLang(rs.getString("lang"));
    u.setPassword(rs.getString("password"));
    u.setIsBot(rs.getInt("is_bot"));
    u.setEmailVerify(rs.getInt("email_verify"));
    Timestamp disable = rs.getTimestamp("disable_at");
    if (disable != null) {
      u.setDisableAt(disable.toLocalDateTime());
    }
    return u;
  }
}
