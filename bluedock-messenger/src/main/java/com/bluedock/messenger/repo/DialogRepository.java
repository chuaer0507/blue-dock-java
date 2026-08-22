package com.bluedock.messenger.repo;

import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DialogRepository {
  private static final RowMapper<Dialog> DIALOG_MAPPER = DialogRepository::mapDialog;
  private static final RowMapper<DialogMessage> MESSAGE_MAPPER = DialogRepository::mapMessage;

  private final JdbcTemplate jdbc;

  public DialogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertDialog(Dialog d) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialogs
          (id, type, group_type, name, avatar, owner_id, link_id, last_message, last_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        d.getId(),
        d.getType(),
        nullToEmpty(d.getGroupType()),
        nullToEmpty(d.getName()),
        nullToEmpty(d.getAvatar()),
        d.getOwnerId(),
        d.getLinkId(),
        nullToEmpty(d.getLastMessage()),
        toTs(d.getLastAt()),
        toTs(d.getCreatedAt()),
        toTs(d.getCreatedAt()));
  }

  public void insertMember(long id, long dialogId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_users
          (id, dialog_id, user_id, unread_count, last_read_message_id, is_top, is_hidden, is_muted, tag, is_deputy, created_at, updated_at)
        VALUES (?, ?, ?, 0, 0, 0, 0, 0, '', 0, ?, ?)
        """,
        id,
        dialogId,
        userId,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void updateDialogMeta(long dialogId, String name, String avatar) {
    jdbc.update(
        """
        UPDATE bluedock_dialogs
        SET name = ?, avatar = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        name == null ? "" : name,
        avatar == null ? "" : avatar,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId);
  }

  public Optional<Dialog> findByGroupLink(String groupType, long linkId) {
    var list =
        jdbc.query(
            """
            SELECT id, type, group_type, name, avatar, owner_id, link_id, last_message, last_at, created_at,
                   0 AS unread_count, 0 AS mention_count, '' AS mention_ids, 0 AS is_top
            FROM bluedock_dialogs
            WHERE type = 'group' AND group_type = ? AND link_id = ? AND deleted_at IS NULL
            LIMIT 1
            """,
            DIALOG_MAPPER,
            groupType,
            linkId);
    return list.stream().findFirst();
  }

  public void updateOwner(long dialogId, long ownerId) {
    jdbc.update(
        """
        UPDATE bluedock_dialogs SET owner_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        ownerId,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId);
  }

  public void softDeleteDialog(long dialogId) {
    jdbc.update(
        """
        UPDATE bluedock_dialogs SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId);
  }

  public void deleteMember(long dialogId, long userId) {
    jdbc.update(
        "DELETE FROM bluedock_dialog_users WHERE dialog_id = ? AND user_id = ?", dialogId, userId);
  }

  public boolean isDeputy(long dialogId, long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_dialog_users
            WHERE dialog_id = ? AND user_id = ? AND is_deputy = 1
            """,
            Integer.class,
            dialogId,
            userId);
    return n != null && n > 0;
  }

  public List<Long> listDeputyUserIds(long dialogId) {
    return jdbc.query(
        """
        SELECT user_id FROM bluedock_dialog_users
        WHERE dialog_id = ? AND is_deputy = 1
        ORDER BY user_id ASC
        """,
        (rs, i) -> rs.getLong(1),
        dialogId);
  }

  public void setDeputy(long dialogId, long userId, boolean deputy) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET is_deputy = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        deputy ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setIsTop(long dialogId, long userId, boolean isTop) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET is_top = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        isTop ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setIsHidden(long dialogId, long userId, boolean isHidden) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET is_hidden = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        isHidden ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setIsMuted(long dialogId, long userId, boolean isMuted) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET is_muted = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        isMuted ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setTag(long dialogId, long userId, String tag) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET tag = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        tag == null ? "" : tag,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public Optional<Map<String, Object>> findUserFlags(long dialogId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT is_top, is_hidden, is_muted, tag, color FROM bluedock_dialog_users
            WHERE dialog_id = ? AND user_id = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("isTop", rs.getInt("is_top"));
              row.put("isHidden", rs.getInt("is_hidden"));
              row.put("isMuted", rs.getInt("is_muted"));
              row.put("tag", rs.getString("tag") == null ? "" : rs.getString("tag"));
              row.put("color", rs.getString("color") == null ? "" : rs.getString("color"));
              return row;
            },
            dialogId,
            userId);
    return list.stream().findFirst();
  }

  public Optional<DialogMessage> findMessage(long messageId) {
    var list =
        jdbc.query(
            """
            SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
            FROM bluedock_dialog_messages
            WHERE id = ? AND deleted_at IS NULL
            """,
            MESSAGE_MAPPER,
            messageId);
    return list.stream().findFirst();
  }

  public void softDeleteMessage(long messageId) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_messages SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        messageId);
  }

  public void updateMessageBody(long messageId, String body, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_messages SET body = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        body,
        toTs(at),
        messageId);
  }

  public Optional<Dialog> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, type, group_type, name, avatar, owner_id, link_id, last_message, last_at, created_at,
                   0 AS unread_count, 0 AS mention_count, '' AS mention_ids, 0 AS is_top
            FROM bluedock_dialogs
            WHERE id = ? AND deleted_at IS NULL
            """,
            DIALOG_MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<Dialog> listForUser(long userId) {
    return jdbc.query(
        """
        SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
               d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color
        FROM bluedock_dialogs d
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
        WHERE d.deleted_at IS NULL AND du.is_hidden = 0
        ORDER BY du.is_top DESC, d.last_at DESC, d.id DESC
        """,
        DIALOG_MAPPER,
        userId);
  }

  /** 列表外：当前用户已隐藏的会话。 */
  public List<Dialog> listHiddenForUser(long userId) {
    return jdbc.query(
        """
        SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
               d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color
        FROM bluedock_dialogs d
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
        WHERE d.deleted_at IS NULL AND du.is_hidden = 1
        ORDER BY d.last_at DESC, d.id DESC
        """,
        DIALOG_MAPPER,
        userId);
  }

  /** 当前用户所在普通个人群；{@code targetUserId>0} 时仅共同群。 */
  public long countCommonUserGroups(long userId, Long targetUserId) {
    if (targetUserId == null || targetUserId <= 0) {
      Long n =
          jdbc.queryForObject(
              """
              SELECT COUNT(*)
              FROM bluedock_dialogs d
              INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
              WHERE d.deleted_at IS NULL AND d.type = 'group' AND d.group_type = 'user'
              """,
              Long.class,
              userId);
      return n == null ? 0L : n;
    }
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM bluedock_dialogs d
            INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
            WHERE d.deleted_at IS NULL AND d.type = 'group' AND d.group_type = 'user'
              AND EXISTS (
                SELECT 1 FROM bluedock_dialog_users t
                WHERE t.dialog_id = d.id AND t.user_id = ?
              )
            """,
            Long.class,
            userId,
            targetUserId);
    return n == null ? 0L : n;
  }

  public List<Dialog> pageCommonUserGroups(long userId, Long targetUserId, int offset, int limit) {
    if (targetUserId == null || targetUserId <= 0) {
      return jdbc.query(
          """
          SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
                 d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color, du.color
          FROM bluedock_dialogs d
          INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
          WHERE d.deleted_at IS NULL AND d.type = 'group' AND d.group_type = 'user'
          ORDER BY du.is_top DESC, d.last_at DESC, d.id DESC
          LIMIT ? OFFSET ?
          """,
          DIALOG_MAPPER,
          userId,
          limit,
          offset);
    }
    return jdbc.query(
        """
        SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
               d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color
        FROM bluedock_dialogs d
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
        WHERE d.deleted_at IS NULL AND d.type = 'group' AND d.group_type = 'user'
          AND EXISTS (
            SELECT 1 FROM bluedock_dialog_users t
            WHERE t.dialog_id = d.id AND t.user_id = ?
          )
        ORDER BY du.is_top DESC, d.last_at DESC, d.id DESC
        LIMIT ? OFFSET ?
        """,
        DIALOG_MAPPER,
        userId,
        targetUserId,
        limit,
        offset);
  }

  /** 按群名搜普通个人群（系统管理员建部门等场景）；{@code key} 空则返回最近 20 条。 */
  public List<Dialog> searchUserGroups(String key, int limit) {
    if (key == null || key.isBlank()) {
      return jdbc.query(
          """
          SELECT id, type, group_type, name, avatar, owner_id, link_id, last_message, last_at, created_at,
                 0 AS unread_count, 0 AS mention_count, '' AS mention_ids, 0 AS is_top
          FROM bluedock_dialogs
          WHERE type = 'group' AND group_type = 'user' AND deleted_at IS NULL
          ORDER BY last_at DESC, id DESC
          LIMIT ?
          """,
          DIALOG_MAPPER,
          limit);
    }
    String like = "%" + key + "%";
    return jdbc.query(
        """
        SELECT id, type, group_type, name, avatar, owner_id, link_id, last_message, last_at, created_at,
               0 AS unread_count, 0 AS mention_count, '' AS mention_ids, 0 AS is_top
        FROM bluedock_dialogs
        WHERE type = 'group' AND group_type = 'user' AND deleted_at IS NULL
          AND name LIKE ?
        ORDER BY last_at DESC, id DESC
        LIMIT ?
        """,
        DIALOG_MAPPER,
        like,
        limit);
  }

  /** 按关键词搜会话名 / 最后消息 / 单聊对方昵称邮箱。 */
  public List<Dialog> searchForUser(long userId, String like, int limit) {
    return jdbc.query(
        """
        SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
               d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color
        FROM bluedock_dialogs d
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
        WHERE d.deleted_at IS NULL
          AND (
            d.name LIKE ?
            OR IFNULL(d.last_message, '') LIKE ?
            OR (
              d.type = 'user'
              AND EXISTS (
                SELECT 1 FROM bluedock_dialog_users o
                INNER JOIN bluedock_users u ON u.id = o.user_id
                WHERE o.dialog_id = d.id AND o.user_id <> ?
                  AND (IFNULL(u.nickname, '') LIKE ? OR IFNULL(u.email, '') LIKE ?)
              )
            )
          )
        ORDER BY du.is_top DESC, d.last_at DESC, d.id DESC
        LIMIT ?
        """,
        DIALOG_MAPPER,
        userId,
        like,
        like,
        userId,
        like,
        like,
        limit);
  }

  /** 按标注关键词搜索会话。 */
  public List<Dialog> searchByTag(long userId, String like, int limit) {
    return jdbc.query(
        """
        SELECT d.id, d.type, d.group_type, d.name, d.avatar, d.owner_id, d.link_id, d.last_message, d.last_at,
               d.created_at, du.unread_count, du.mention_count, du.mention_ids, du.is_top, du.color
        FROM bluedock_dialogs d
        INNER JOIN bluedock_dialog_users du ON du.dialog_id = d.id AND du.user_id = ?
        WHERE d.deleted_at IS NULL AND du.tag <> '' AND du.tag LIKE ?
        ORDER BY d.last_at DESC, d.id DESC
        LIMIT ?
        """,
        DIALOG_MAPPER,
        userId,
        like,
        limit);
  }

  public boolean isMember(long dialogId, long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_dialog_users WHERE dialog_id = ? AND user_id = ?",
            Integer.class,
            dialogId,
            userId);
    return n != null && n > 0;
  }

  public List<Long> listMemberUserIds(long dialogId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_dialog_users WHERE dialog_id = ? ORDER BY user_id ASC",
        (rs, i) -> rs.getLong(1),
        dialogId);
  }

  /** 查找两人单聊（type=user，恰好两人且均在会话中）。 */
  public Optional<Long> findUserDialogId(long userIdA, long userIdB) {
    var list =
        jdbc.query(
            """
            SELECT d.id
            FROM bluedock_dialogs d
            INNER JOIN bluedock_dialog_users a ON a.dialog_id = d.id AND a.user_id = ?
            INNER JOIN bluedock_dialog_users b ON b.dialog_id = d.id AND b.user_id = ?
            WHERE d.type = 'user' AND d.deleted_at IS NULL
              AND (SELECT COUNT(1) FROM bluedock_dialog_users u WHERE u.dialog_id = d.id) = 2
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userIdA,
            userIdB);
    return list.stream().findFirst();
  }

  public void insertMessage(DialogMessage m) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_messages
          (id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        m.getId(),
        m.getDialogId(),
        m.getUserId(),
        m.getType(),
        m.getBody(),
        m.getReplyId(),
        m.getTagUserId(),
        toTs(m.getCreatedAt()),
        toTs(m.getCreatedAt()));
  }

  public void touchDialog(long dialogId, String lastMessage, LocalDateTime lastAt) {
    String preview = lastMessage == null ? "" : lastMessage;
    if (preview.length() > 200) {
      preview = preview.substring(0, 200);
    }
    jdbc.update(
        """
        UPDATE bluedock_dialogs
        SET last_message = ?, last_at = ?, updated_at = ?
        WHERE id = ?
        """,
        preview,
        toTs(lastAt),
        toTs(lastAt),
        dialogId);
  }

  public void bumpUnreadExcept(long dialogId, long exceptUserId) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users
        SET unread_count = unread_count + 1, updated_at = ?
        WHERE dialog_id = ? AND user_id <> ?
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        exceptUserId);
  }

  public void clearUnread(long dialogId, long userId, long lastReadMessageId) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users
        SET unread_count = 0, mention_count = 0, mention_ids = '', last_read_message_id = ?,
            mark_unread = 0, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        lastReadMessageId,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setMarkUnread(long dialogId, long userId, boolean markUnread) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET mark_unread = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        markUnread ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void setMemberColor(long dialogId, long userId, String color) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET color = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        color == null ? "" : color.trim(),
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public Optional<Map<String, Object>> findMemberFlags(long dialogId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT unread_count, mention_count, mention_ids, last_read_message_id, mark_unread, color, tag,
                   is_muted, is_top, is_hidden, updated_at
            FROM bluedock_dialog_users
            WHERE dialog_id = ? AND user_id = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("unreadCount", rs.getInt("unread_count"));
              row.put("mentionCount", rs.getInt("mention_count"));
              row.put("mentionIds", rs.getString("mention_ids") == null ? "" : rs.getString("mention_ids"));
              row.put("lastReadMessageId", rs.getLong("last_read_message_id"));
              row.put("markUnread", rs.getInt("mark_unread"));
              row.put("color", rs.getString("color") == null ? "" : rs.getString("color"));
              row.put("tag", rs.getString("tag") == null ? "" : rs.getString("tag"));
              row.put("isMuted", rs.getInt("is_muted"));
              row.put("isTop", rs.getInt("is_top"));
              row.put("isHidden", rs.getInt("is_hidden"));
              Timestamp u = rs.getTimestamp("updated_at");
              row.put("updatedAt", u == null ? null : u.toLocalDateTime());
              return row;
            },
            dialogId,
            userId);
    return list.stream().findFirst();
  }

  public void updateMessageTagUserId(long messageId, long tagUserId, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_messages SET tag_user_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        tagUserId,
        toTs(at),
        messageId);
  }

  public Optional<Map<String, Object>> findTranslation(long messageId, String language) {
    var list =
        jdbc.query(
            """
            SELECT message_id, language, content FROM bluedock_dialog_message_translations
            WHERE message_id = ? AND language = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("messageId", rs.getLong("message_id"));
              row.put("language", rs.getString("language"));
              row.put("content", rs.getString("content") == null ? "" : rs.getString("content"));
              return row;
            },
            messageId,
            language);
    return list.stream().findFirst();
  }

  public void deleteTranslation(long messageId, String language) {
    jdbc.update(
        """
        DELETE FROM bluedock_dialog_message_translations
        WHERE message_id = ? AND language = ?
        """,
        messageId,
        language);
  }

  public void upsertTranslation(
      long id, long dialogId, long messageId, String language, String content, LocalDateTime at) {
    int n =
        jdbc.update(
            """
            UPDATE bluedock_dialog_message_translations
            SET content = ?, updated_at = ?
            WHERE message_id = ? AND language = ?
            """,
            content,
            toTs(at),
            messageId,
            language);
    if (n > 0) {
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_message_translations
          (id, dialog_id, message_id, language, content, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        dialogId,
        messageId,
        language,
        content,
        toTs(at),
        toTs(at));
  }

  /** 对指定成员累加 @ 未读并追加 messageId。 */
  public void bumpMention(long dialogId, long userId, long messageId) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users
        SET mention_count = mention_count + 1,
            mention_ids = TRIM(BOTH ',' FROM CONCAT(IFNULL(NULLIF(mention_ids, ''), ''), ',', ?)),
            updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        String.valueOf(messageId),
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public List<Map<String, Object>> listUnreadByUser(long userId) {
    return jdbc.query(
        """
        SELECT dialog_id, unread_count, mention_count, mention_ids, last_read_message_id
        FROM bluedock_dialog_users
        WHERE user_id = ? AND (unread_count > 0 OR mention_count > 0) AND is_hidden = 0
        ORDER BY mention_count DESC, unread_count DESC, dialog_id DESC
        """,
        (rs, i) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("dialogId", rs.getLong("dialog_id"));
          row.put("unreadCount", rs.getInt("unread_count"));
          row.put("mentionCount", rs.getInt("mention_count"));
          row.put("mentionIds", rs.getString("mention_ids"));
          row.put("lastReadMessageId", rs.getLong("last_read_message_id"));
          return row;
        },
        userId);
  }

  /** userId → mute（1=免打扰，写入读回执 silence）。 */
  public Map<Long, Boolean> listMemberMutes(long dialogId) {
    Map<Long, Boolean> out = new LinkedHashMap<>();
    jdbc.query(
        "SELECT user_id, is_muted FROM bluedock_dialog_users WHERE dialog_id = ?",
        rs -> {
          out.put(rs.getLong("user_id"), rs.getInt("is_muted") == 1);
        },
        dialogId);
    return out;
  }

  /** @提及强制提醒：清除读回执静默标记。 */
  public void clearMessageReadSilent(long messageId, Collection<Long> userIds) {
    if (messageId <= 0 || userIds == null || userIds.isEmpty()) {
      return;
    }
    List<Long> ids = new ArrayList<>();
    for (Long id : userIds) {
      if (id != null && id > 0) {
        ids.add(id);
      }
    }
    if (ids.isEmpty()) {
      return;
    }
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Object> args = new ArrayList<>(ids.size() + 1);
    args.add(messageId);
    args.addAll(ids);
    jdbc.update(
        "UPDATE bluedock_dialog_message_reads SET is_silent = 0 WHERE message_id = ? AND user_id IN ("
            + placeholders
            + ")",
        args.toArray());
  }

  /** 用户可见会话未读总和（角标）。 */
  public int sumUnreadForUser(long userId) {
    if (userId <= 0) {
      return 0;
    }
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(unread_count), 0) FROM bluedock_dialog_users
            WHERE user_id = ? AND IFNULL(is_hidden, 0) = 0
            """,
            Integer.class,
            userId);
    return n == null ? 0 : Math.min(99, Math.max(0, n));
  }

  public void insertMessageRead(
      long id, long messageId, long dialogId, long userId, LocalDateTime readAt, boolean silence) {
    insertMessageRead(id, messageId, dialogId, userId, readAt, silence, 0);
  }

  public void insertMessageRead(
      long id,
      long messageId,
      long dialogId,
      long userId,
      LocalDateTime readAt,
      boolean silence,
      int dot) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_message_reads
          (id, message_id, dialog_id, user_id, read_at, is_silent, email_sent, dot, created_at)
        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        messageId,
        dialogId,
        userId,
        toTs(readAt),
        silence ? 1 : 0,
        dot > 0 ? 1 : 0,
        toTs(LocalDateTime.now()));
  }

  /** 兼容旧调用：非静默。 */
  public void insertMessageRead(long id, long messageId, long dialogId, long userId, LocalDateTime readAt) {
    insertMessageRead(id, messageId, dialogId, userId, readAt, false, 0);
  }

  /** 清除当前用户对某消息的红点。 */
  public void clearMessageDot(long messageId, long userId) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_message_reads SET dot = 0
        WHERE message_id = ? AND user_id = ?
        """,
        messageId,
        userId);
  }

  public void markMessageReadsUpTo(long dialogId, long userId, long upToMessageId, LocalDateTime at) {
    List<Long> messageIds =
        jdbc.query(
            """
            SELECT id FROM bluedock_dialog_messages
            WHERE dialog_id = ? AND deleted_at IS NULL AND id <= ?
              AND id NOT IN (
                SELECT message_id FROM bluedock_dialog_message_reads
                WHERE dialog_id = ? AND user_id = ? AND read_at IS NOT NULL
              )
            ORDER BY id ASC
            LIMIT 500
            """,
            (rs, i) -> rs.getLong(1),
            dialogId,
            upToMessageId,
            dialogId,
            userId);
    for (Long messageId : messageIds) {
      int updated =
          jdbc.update(
              """
              UPDATE bluedock_dialog_message_reads
              SET read_at = ?
              WHERE message_id = ? AND user_id = ? AND read_at IS NULL
              """,
              toTs(at),
              messageId,
              userId);
      if (updated == 0) {
        // 行不存在则插入已读
        try {
          insertMessageRead(IdGenerator.nextId(), messageId, dialogId, userId, at);
        } catch (Exception ignored) {
          jdbc.update(
              """
              UPDATE bluedock_dialog_message_reads
              SET read_at = ?
              WHERE message_id = ? AND user_id = ?
              """,
              toTs(at),
              messageId,
              userId);
        }
      }
    }
  }

  /** 将 {@code fromMessageId}（含）及之后的未读回执标为已读。 */
  public void markMessageReadsFrom(long dialogId, long userId, long fromMessageId, LocalDateTime at) {
    long maxId = maxMessageId(dialogId);
    if (maxId <= 0) {
      return;
    }
    if (fromMessageId <= 0) {
      markMessageReadsUpTo(dialogId, userId, maxId, at);
      return;
    }
    List<Long> messageIds =
        jdbc.query(
            """
            SELECT id FROM bluedock_dialog_messages
            WHERE dialog_id = ? AND deleted_at IS NULL AND id >= ? AND id <= ?
            ORDER BY id ASC
            LIMIT 500
            """,
            (rs, i) -> rs.getLong(1),
            dialogId,
            fromMessageId,
            maxId);
    for (Long messageId : messageIds) {
      int updated =
          jdbc.update(
              """
              UPDATE bluedock_dialog_message_reads
              SET read_at = ?
              WHERE message_id = ? AND user_id = ? AND read_at IS NULL
              """,
              toTs(at),
              messageId,
              userId);
      if (updated == 0) {
        try {
          insertMessageRead(IdGenerator.nextId(), messageId, dialogId, userId, at);
        } catch (Exception ignored) {
          jdbc.update(
              """
              UPDATE bluedock_dialog_message_reads
              SET read_at = ?
              WHERE message_id = ? AND user_id = ?
              """,
              toTs(at),
              messageId,
              userId);
        }
      }
    }
  }

  public List<Long> listReaders(long messageId) {
    return jdbc.query(
        """
        SELECT user_id FROM bluedock_dialog_message_reads
        WHERE message_id = ? AND read_at IS NOT NULL
        ORDER BY read_at ASC
        """,
        (rs, i) -> rs.getLong(1),
        messageId);
  }

  public long maxMessageId(long dialogId) {
    Long v =
        jdbc.queryForObject(
            """
            SELECT COALESCE(MAX(id), 0) FROM bluedock_dialog_messages
            WHERE dialog_id = ? AND deleted_at IS NULL
            """,
            Long.class,
            dialogId);
    return v == null ? 0L : v;
  }

  public Optional<Map<String, Object>> findFileMeta(long fileId) {
    var list =
        jdbc.query(
            """
            SELECT id, name, type, extension, size, path, user_id
            FROM bluedock_files
            WHERE id = ? AND deleted_at IS NULL
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("name", rs.getString("name"));
              m.put("type", rs.getString("type"));
              m.put("extension", rs.getString("extension"));
              m.put("size", rs.getLong("size"));
              m.put("path", rs.getString("path"));
              m.put("userId", rs.getLong("user_id"));
              return m;
            },
            fileId);
    return list.stream().findFirst();
  }

  public List<DialogMessage> findMessagesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String placeholders = ids.stream().map(x -> "?").reduce((a, b) -> a + "," + b).orElse("?");
    Object[] args = ids.toArray();
    return jdbc.query(
        """
        SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
        FROM bluedock_dialog_messages
        WHERE deleted_at IS NULL AND id IN (%s)
        ORDER BY id ASC
        """
            .formatted(placeholders),
        MESSAGE_MAPPER,
        args);
  }

  public void insertEmoji(long id, long messageId, long userId, String symbol, LocalDateTime at) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_message_emojis (id, message_id, user_id, symbol, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        id,
        messageId,
        userId,
        symbol,
        toTs(at));
  }

  public int deleteEmoji(long messageId, long userId, String symbol) {
    return jdbc.update(
        """
        DELETE FROM bluedock_dialog_message_emojis
        WHERE message_id = ? AND user_id = ? AND symbol = ?
        """,
        messageId,
        userId,
        symbol);
  }

  public List<Map<String, Object>> listEmojis(long messageId) {
    return jdbc.query(
        """
        SELECT user_id, symbol, created_at
        FROM bluedock_dialog_message_emojis
        WHERE message_id = ?
        ORDER BY created_at ASC, id ASC
        """,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("userId", rs.getLong("user_id"));
          m.put("symbol", rs.getString("symbol"));
          Timestamp c = rs.getTimestamp("created_at");
          m.put("createdAt", c == null ? null : c.toLocalDateTime());
          return m;
        },
        messageId);
  }

  public List<Map<String, Object>> listEmojisByMessageIds(List<Long> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return List.of();
    }
    String placeholders =
        messageIds.stream().map(x -> "?").reduce((a, b) -> a + "," + b).orElse("?");
    return jdbc.query(
        """
        SELECT message_id, user_id, symbol, created_at
        FROM bluedock_dialog_message_emojis
        WHERE message_id IN (%s)
        ORDER BY created_at ASC, id ASC
        """
            .formatted(placeholders),
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("messageId", rs.getLong("message_id"));
          m.put("userId", rs.getLong("user_id"));
          m.put("symbol", rs.getString("symbol"));
          Timestamp c = rs.getTimestamp("created_at");
          m.put("createdAt", c == null ? null : c.toLocalDateTime());
          return m;
        },
        messageIds.toArray());
  }

  public void insertMessageTop(long id, long dialogId, long messageId, long userId, LocalDateTime at) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_message_tops (id, dialog_id, message_id, user_id, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        id,
        dialogId,
        messageId,
        userId,
        toTs(at));
  }

  public int deleteMessageTop(long dialogId, long messageId) {
    return jdbc.update(
        """
        DELETE FROM bluedock_dialog_message_tops WHERE dialog_id = ? AND message_id = ?
        """,
        dialogId,
        messageId);
  }

  public List<Long> listTopMessageIds(long dialogId) {
    return jdbc.query(
        """
        SELECT message_id FROM bluedock_dialog_message_tops
        WHERE dialog_id = ?
        ORDER BY created_at DESC, id DESC
        """,
        (rs, i) -> rs.getLong(1),
        dialogId);
  }

  public void insertTodo(
      long id, long messageId, long dialogId, long userId, LocalDateTime at) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_message_todos
          (id, message_id, dialog_id, user_id, remind_at, done_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)
        """,
        id,
        messageId,
        dialogId,
        userId,
        toTs(at),
        toTs(at));
  }

  public int deleteTodo(long messageId, long userId) {
    return jdbc.update(
        """
        DELETE FROM bluedock_dialog_message_todos
        WHERE message_id = ? AND user_id = ? AND done_at IS NULL
        """,
        messageId,
        userId);
  }

  public Optional<Map<String, Object>> findTodo(long messageId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT id, message_id, dialog_id, user_id, remind_at, done_at, created_at, updated_at
            FROM bluedock_dialog_message_todos
            WHERE message_id = ? AND user_id = ?
            """,
            (rs, i) -> mapTodo(rs),
            messageId,
            userId);
    return list.stream().findFirst();
  }

  public int markTodoDone(long messageId, long userId, LocalDateTime at) {
    return jdbc.update(
        """
        UPDATE bluedock_dialog_message_todos
        SET done_at = ?, updated_at = ?
        WHERE message_id = ? AND user_id = ? AND done_at IS NULL
        """,
        toTs(at),
        toTs(at),
        messageId,
        userId);
  }

  public int updateTodoRemind(long messageId, long userId, LocalDateTime remindAt, LocalDateTime at) {
    return jdbc.update(
        """
        UPDATE bluedock_dialog_message_todos
        SET remind_at = ?, updated_at = ?
        WHERE message_id = ? AND user_id = ? AND done_at IS NULL
        """,
        toTs(remindAt),
        toTs(at),
        messageId,
        userId);
  }

  /** 到期未完成的待办（remind_at &lt;= now）。 */
  public List<Map<String, Object>> listDueTodos(LocalDateTime now, int limit) {
    int take = Math.min(Math.max(limit, 1), 200);
    return jdbc.query(
        """
        SELECT id, message_id, dialog_id, user_id, remind_at, done_at, created_at, updated_at
        FROM bluedock_dialog_message_todos
        WHERE done_at IS NULL
          AND remind_at IS NOT NULL
          AND remind_at <= ?
        ORDER BY remind_at ASC, id ASC
        LIMIT ?
        """,
        (rs, i) -> mapTodo(rs),
        toTs(now),
        take);
  }

  /** 批量软删机器人发出的过期消息；返回影响行数。 */
  public int softDeleteBotMessagesBefore(long botUserId, LocalDateTime before, int limit) {
    int take = Math.min(Math.max(limit, 1), 1000);
    LocalDateTime now = LocalDateTime.now();
    return jdbc.update(
        """
        UPDATE bluedock_dialog_messages
        SET deleted_at = ?, updated_at = ?
        WHERE user_id = ?
          AND deleted_at IS NULL
          AND created_at < ?
        ORDER BY id ASC
        LIMIT ?
        """,
        toTs(now),
        toTs(now),
        botUserId,
        toTs(before),
        take);
  }

  /** 自建机器人清理候选。 */
  public List<Map<String, Object>> listUserBotsForClear(LocalDateTime now, int limit) {
    int take = Math.min(Math.max(limit, 1), 200);
    return jdbc.query(
        """
        SELECT id, user_id AS ownerId, bot_id AS botId, clear_day AS clearDay, clear_at AS clearAt
        FROM bluedock_user_bots
        WHERE clear_day > 0
          AND (clear_at IS NULL OR clear_at <= ?)
        ORDER BY clear_at IS NULL DESC, clear_at ASC, id ASC
        LIMIT ?
        """,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          m.put("ownerId", rs.getLong("ownerId"));
          m.put("botId", rs.getLong("botId"));
          m.put("clearDay", rs.getInt("clearDay"));
          Timestamp c = rs.getTimestamp("clearAt");
          m.put("clearAt", c == null ? null : c.toLocalDateTime());
          return m;
        },
        toTs(now),
        take);
  }

  public void updateUserBotClearAt(long id, LocalDateTime clearAt) {
    jdbc.update(
        """
        UPDATE bluedock_user_bots SET clear_at = ?, updated_at = ? WHERE id = ?
        """,
        toTs(clearAt),
        toTs(LocalDateTime.now()),
        id);
  }

  public List<Map<String, Object>> listTodos(long userId, Long dialogId, boolean includeDone) {
    if (dialogId != null && dialogId > 0) {
      return jdbc.query(
          """
          SELECT id, message_id, dialog_id, user_id, remind_at, done_at, created_at, updated_at
          FROM bluedock_dialog_message_todos
          WHERE user_id = ? AND dialog_id = ?
            AND (? OR done_at IS NULL)
          ORDER BY done_at IS NULL DESC, remind_at IS NULL ASC, remind_at ASC, id DESC
          LIMIT 200
          """,
          (rs, i) -> mapTodo(rs),
          userId,
          dialogId,
          includeDone);
    }
    return jdbc.query(
        """
        SELECT id, message_id, dialog_id, user_id, remind_at, done_at, created_at, updated_at
        FROM bluedock_dialog_message_todos
        WHERE user_id = ?
          AND (? OR done_at IS NULL)
        ORDER BY done_at IS NULL DESC, remind_at IS NULL ASC, remind_at ASC, id DESC
        LIMIT 200
        """,
        (rs, i) -> mapTodo(rs),
        userId,
        includeDone);
  }

  private static Map<String, Object> mapTodo(ResultSet rs) throws SQLException {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", rs.getLong("id"));
    m.put("messageId", rs.getLong("message_id"));
    m.put("dialogId", rs.getLong("dialog_id"));
    m.put("userId", rs.getLong("user_id"));
    Timestamp remind = rs.getTimestamp("remind_at");
    Timestamp done = rs.getTimestamp("done_at");
    Timestamp created = rs.getTimestamp("created_at");
    Timestamp updated = rs.getTimestamp("updated_at");
    m.put("remindAt", remind == null ? null : remind.toLocalDateTime());
    m.put("doneAt", done == null ? null : done.toLocalDateTime());
    m.put("createdAt", created == null ? null : created.toLocalDateTime());
    m.put("updatedAt", updated == null ? null : updated.toLocalDateTime());
    return m;
  }

  public List<DialogMessage> listMessages(long dialogId, Long beforeId, int take) {
    int limit = Math.min(Math.max(take, 1), 100);
    if (beforeId != null && beforeId > 0) {
      return jdbc.query(
          """
          SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
          FROM bluedock_dialog_messages
          WHERE dialog_id = ? AND deleted_at IS NULL AND id < ?
          ORDER BY id DESC
          LIMIT ?
          """,
          MESSAGE_MAPPER,
          dialogId,
          beforeId,
          limit);
    }
    return jdbc.query(
        """
        SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
        FROM bluedock_dialog_messages
        WHERE dialog_id = ? AND deleted_at IS NULL
        ORDER BY id DESC
        LIMIT ?
        """,
        MESSAGE_MAPPER,
        dialogId,
        limit);
  }

  /** 某会话在 {@code afterId} 之后的新消息（id 降序，最多 take 条）。 */
  public List<DialogMessage> listMessagesAfter(long dialogId, long afterId, int take) {
    int limit = Math.min(Math.max(take, 1), 50);
    if (afterId > 0) {
      return jdbc.query(
          """
          SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
          FROM bluedock_dialog_messages
          WHERE dialog_id = ? AND deleted_at IS NULL AND id > ?
          ORDER BY id DESC
          LIMIT ?
          """,
          MESSAGE_MAPPER,
          dialogId,
          afterId,
          limit);
    }
    return jdbc.query(
        """
        SELECT id, dialog_id, user_id, type, body, reply_id, tag_user_id, created_at, updated_at
        FROM bluedock_dialog_messages
        WHERE dialog_id = ? AND deleted_at IS NULL
        ORDER BY id DESC
        LIMIT ?
        """,
        MESSAGE_MAPPER,
        dialogId,
        limit);
  }

  private static Timestamp toTs(LocalDateTime v) {
    return v == null ? null : Timestamp.valueOf(v);
  }

  /** 会话级群禁言：1=禁言。 */
  public int findChatMuted(long dialogId) {
    Integer v =
        jdbc.query(
            """
            SELECT is_chat_muted FROM bluedock_dialog_configs WHERE dialog_id = ? LIMIT 1
            """,
            rs -> rs.next() ? rs.getInt(1) : null,
            dialogId);
    return v == null ? 0 : v;
  }

  public void upsertChatMuted(long id, long dialogId, boolean muted, LocalDateTime now) {
    int n =
        jdbc.update(
            """
            UPDATE bluedock_dialog_configs
            SET is_chat_muted = ?, updated_at = ?
            WHERE dialog_id = ?
            """,
            muted ? 1 : 0,
            Timestamp.valueOf(now),
            dialogId);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_dialog_configs (id, dialog_id, is_chat_muted, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          id,
          dialogId,
          muted ? 1 : 0,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public String findUserSessionKey(long dialogId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT session_key FROM bluedock_dialog_users
            WHERE dialog_id = ? AND user_id = ?
            LIMIT 1
            """,
            (rs, i) -> rs.getString(1) == null ? "" : rs.getString(1),
            dialogId,
            userId);
    return list.isEmpty() ? "" : list.get(0);
  }

  public void setUserSessionKey(long dialogId, long userId, String sessionKey) {
    jdbc.update(
        """
        UPDATE bluedock_dialog_users SET session_key = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ?
        """,
        sessionKey == null ? "" : sessionKey,
        Timestamp.valueOf(LocalDateTime.now()),
        dialogId,
        userId);
  }

  public void insertDialogSession(
      long id, long dialogId, long userId, String sessionKey, String title, LocalDateTime now) {
    jdbc.update(
        """
        INSERT INTO bluedock_dialog_sessions
          (id, dialog_id, user_id, session_key, title, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        dialogId,
        userId,
        sessionKey,
        title == null ? "" : title,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public Optional<Map<String, Object>> findDialogSession(
      long dialogId, long userId, String sessionKey) {
    var list =
        jdbc.query(
            """
            SELECT id, dialog_id, user_id, session_key, title, created_at, updated_at
            FROM bluedock_dialog_sessions
            WHERE dialog_id = ? AND user_id = ? AND session_key = ?
            LIMIT 1
            """,
            (rs, i) -> mapSessionRow(rs),
            dialogId,
            userId,
            sessionKey);
    return list.stream().findFirst();
  }

  public List<Map<String, Object>> listDialogSessions(long dialogId, long userId) {
    return jdbc.query(
        """
        SELECT id, dialog_id, user_id, session_key, title, created_at, updated_at
        FROM bluedock_dialog_sessions
        WHERE dialog_id = ? AND user_id = ?
        ORDER BY updated_at DESC, id DESC
        """,
        (rs, i) -> mapSessionRow(rs),
        dialogId,
        userId);
  }

  public int updateDialogSessionTitle(
      long dialogId, long userId, String sessionKey, String title, LocalDateTime now) {
    return jdbc.update(
        """
        UPDATE bluedock_dialog_sessions
        SET title = ?, updated_at = ?
        WHERE dialog_id = ? AND user_id = ? AND session_key = ?
        """,
        title == null ? "" : title,
        Timestamp.valueOf(now),
        dialogId,
        userId,
        sessionKey);
  }

  public int touchDialogSession(long dialogId, long userId, String sessionKey, LocalDateTime now) {
    return jdbc.update(
        """
        UPDATE bluedock_dialog_sessions SET updated_at = ?
        WHERE dialog_id = ? AND user_id = ? AND session_key = ?
        """,
        Timestamp.valueOf(now),
        dialogId,
        userId,
        sessionKey);
  }

  private static Map<String, Object> mapSessionRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", rs.getLong("id"));
    row.put("dialogId", rs.getLong("dialog_id"));
    row.put("userId", rs.getLong("user_id"));
    row.put("sessionKey", rs.getString("session_key") == null ? "" : rs.getString("session_key"));
    row.put("title", rs.getString("title") == null ? "" : rs.getString("title"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    row.put("createdAt", c == null ? null : c.toLocalDateTime());
    row.put("updatedAt", u == null ? null : u.toLocalDateTime());
    return row;
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static Dialog mapDialog(ResultSet rs, int rowNum) throws SQLException {
    Dialog d = new Dialog();
    d.setId(rs.getLong("id"));
    d.setType(rs.getString("type"));
    d.setGroupType(rs.getString("group_type"));
    d.setName(rs.getString("name"));
    d.setAvatar(rs.getString("avatar"));
    d.setOwnerId(rs.getLong("owner_id"));
    d.setLinkId(rs.getLong("link_id"));
    d.setLastMessage(rs.getString("last_message"));
    Timestamp lastAt = rs.getTimestamp("last_at");
    if (lastAt != null) {
      d.setLastAt(lastAt.toLocalDateTime());
    }
    Timestamp created = rs.getTimestamp("created_at");
    if (created != null) {
      d.setCreatedAt(created.toLocalDateTime());
    }
    d.setUnreadCount(rs.getInt("unread_count"));
    d.setMentionCount(rs.getInt("mention_count"));
    String mids = rs.getString("mention_ids");
    d.setMentionIds(mids == null ? "" : mids);
    d.setIsTop(rs.getInt("is_top"));
    try {
      String color = rs.getString("color");
      d.setColor(color == null ? "" : color);
    } catch (SQLException ignored) {
      d.setColor("");
    }
    return d;
  }

  private static DialogMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
    DialogMessage m = new DialogMessage();
    m.setId(rs.getLong("id"));
    m.setDialogId(rs.getLong("dialog_id"));
    m.setUserId(rs.getLong("user_id"));
    m.setType(rs.getString("type"));
    m.setBody(rs.getString("body"));
    m.setReplyId(rs.getLong("reply_id"));
    try {
      m.setTagUserId(rs.getLong("tag_user_id"));
    } catch (SQLException ignored) {
      m.setTagUserId(0L);
    }
    Timestamp created = rs.getTimestamp("created_at");
    if (created != null) {
      m.setCreatedAt(created.toLocalDateTime());
    }
    Timestamp updated = rs.getTimestamp("updated_at");
    if (updated != null) {
      m.setUpdatedAt(updated.toLocalDateTime());
    } else if (created != null) {
      m.setUpdatedAt(created.toLocalDateTime());
    }
    return m;
  }
}
