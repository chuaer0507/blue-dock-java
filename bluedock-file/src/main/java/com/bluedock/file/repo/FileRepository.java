package com.bluedock.file.repo;

import com.bluedock.file.domain.FileEntry;
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
public class FileRepository {
  private static final RowMapper<FileEntry> MAPPER = FileRepository::mapRow;

  private final JdbcTemplate jdbc;

  public FileRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(FileEntry f) {
    jdbc.update(
        """
        INSERT INTO bluedock_files
          (id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
           created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        f.getId(),
        f.getParentId(),
        f.getName(),
        f.getType(),
        f.getExtension(),
        f.getSize(),
        f.getHash(),
        f.getPath(),
        f.getUserId(),
        f.getCreatedUserId(),
        f.getIsShared(),
        Timestamp.valueOf(f.getCreatedAt()),
        Timestamp.valueOf(f.getUpdatedAt()));
  }

  public Optional<FileEntry> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
                   created_at, updated_at, deleted_at
            FROM bluedock_files
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  /** 含软删；用于回收站恢复。 */
  public Optional<FileEntry> findIncludingDeleted(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
                   created_at, updated_at, deleted_at
            FROM bluedock_files
            WHERE id = ?
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  /** 回收站根：自身已删，且父级未删（或无父级）。 */
  public List<FileEntry> listTrashRoots(long userId) {
    return jdbc.query(
        """
        SELECT f.id, f.parent_id, f.name, f.type, f.extension, f.size, f.hash, f.path, f.user_id,
               f.created_user_id, f.is_shared, f.created_at, f.updated_at, f.deleted_at
        FROM bluedock_files f
        WHERE f.user_id = ? AND f.deleted_at IS NOT NULL
          AND (
            f.parent_id = 0
            OR NOT EXISTS (
              SELECT 1 FROM bluedock_files p
              WHERE p.id = f.parent_id AND p.deleted_at IS NOT NULL
            )
          )
        ORDER BY f.deleted_at DESC, f.id DESC
        """,
        MAPPER,
        userId);
  }

  public Optional<FileEntry> findByUserAndHash(long userId, String hash) {
    var list =
        jdbc.query(
            """
            SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
                   created_at, updated_at, deleted_at
            FROM bluedock_files
            WHERE user_id = ? AND hash = ? AND type <> 'folder' AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """,
            MAPPER,
            userId,
            hash);
    return list.stream().findFirst();
  }

  public List<FileEntry> listByParent(long userId, long parentId) {
    return jdbc.query(
        """
        SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
               created_at, updated_at, deleted_at
        FROM bluedock_files
        WHERE user_id = ? AND parent_id = ? AND deleted_at IS NULL
        ORDER BY type = 'folder' DESC, id DESC
        """,
        MAPPER,
        userId,
        parentId);
  }

  public List<FileEntry> listByParentAny(long parentId) {
    return jdbc.query(
        """
        SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
               created_at, updated_at, deleted_at
        FROM bluedock_files
        WHERE parent_id = ? AND deleted_at IS NULL
        ORDER BY type = 'folder' DESC, id DESC
        """,
        MAPPER,
        parentId);
  }

  public List<FileEntry> listSharedRoots(long userId) {
    return jdbc.query(
        """
        SELECT f.id, f.parent_id, f.name, f.type, f.extension, f.size, f.hash, f.path, f.user_id, f.created_user_id,
               f.is_shared, f.created_at, f.updated_at, f.deleted_at
        FROM bluedock_files f
        INNER JOIN bluedock_file_users u ON u.file_id = f.id AND u.user_id = ? AND u.deleted_at IS NULL
        WHERE f.deleted_at IS NULL
        ORDER BY f.id DESC
        """,
        MAPPER,
        userId);
  }

  public int countByParent(long userId, long parentId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM bluedock_files
            WHERE user_id = ? AND parent_id = ? AND deleted_at IS NULL
            """,
            Integer.class,
            userId,
            parentId);
    return n == null ? 0 : n;
  }

  public void softDelete(long id, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(at),
        Timestamp.valueOf(at),
        id);
  }

  public void clearDeleted(long id, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET deleted_at = NULL, updated_at = ?
        WHERE id = ? AND deleted_at IS NOT NULL
        """,
        Timestamp.valueOf(at),
        id);
  }

  /** 恢复节点并可改挂载父级（父已删时挂到根）。 */
  public void restoreWithParent(long id, long parentId, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET deleted_at = NULL, parent_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NOT NULL
        """,
        parentId,
        Timestamp.valueOf(at),
        id);
  }

  public List<Long> listDeletedChildIds(long userId, long parentId) {
    return jdbc.query(
        """
        SELECT id FROM bluedock_files
        WHERE user_id = ? AND parent_id = ? AND deleted_at IS NOT NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId,
        parentId);
  }

  public void rename(long id, String name, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET name = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        name,
        Timestamp.valueOf(at),
        id);
  }

  public void move(long id, long parentId, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET parent_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        parentId,
        Timestamp.valueOf(at),
        id);
  }

  public void updateSize(long id, long size, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET size = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        size,
        Timestamp.valueOf(at),
        id);
  }

  public void updateStorage(long id, String path, String hash, long size, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET path = ?, hash = ?, size = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        path,
        hash,
        size,
        Timestamp.valueOf(at),
        id);
  }

  public void updateShare(long id, int share, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_files SET is_shared = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        share,
        Timestamp.valueOf(at),
        id);
  }

  public List<FileEntry> searchByName(long userId, String key, int limit) {
    String like = "%" + escape(key) + "%";
    return jdbc.query(
        """
        SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
               created_at, updated_at, deleted_at
        FROM bluedock_files
        WHERE user_id = ? AND deleted_at IS NULL AND name LIKE ? ESCAPE '\\'
        ORDER BY id DESC
        LIMIT ?
        """,
        MAPPER,
        userId,
        like,
        limit);
  }

  public Optional<FileEntry> findByUserAndPath(long userId, String path) {
    var list =
        jdbc.query(
            """
            SELECT id, parent_id, name, type, extension, size, hash, path, user_id, created_user_id, is_shared,
                   created_at, updated_at, deleted_at
            FROM bluedock_files
            WHERE user_id = ? AND path = ? AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """,
            MAPPER,
            userId,
            path);
    return list.stream().findFirst();
  }

  public List<Long> listChildIds(long userId, long parentId) {
    return jdbc.query(
        """
        SELECT id FROM bluedock_files
        WHERE user_id = ? AND parent_id = ? AND deleted_at IS NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId,
        parentId);
  }

  public void softDeleteTree(long userId, long rootId, LocalDateTime at) {
    softDelete(rootId, at);
    for (Long child : listChildIds(userId, rootId)) {
      softDeleteTree(userId, child, at);
    }
  }

  private static String escape(String key) {
    return key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static FileEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
    FileEntry f = new FileEntry();
    f.setId(rs.getLong("id"));
    f.setParentId(rs.getLong("parent_id"));
    f.setName(rs.getString("name"));
    f.setType(rs.getString("type"));
    f.setExtension(rs.getString("extension"));
    f.setSize(rs.getLong("size"));
    f.setHash(rs.getString("hash"));
    f.setPath(rs.getString("path"));
    f.setUserId(rs.getLong("user_id"));
    f.setCreatedUserId(rs.getLong("created_user_id"));
    f.setIsShared(rs.getInt("is_shared"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    Timestamp d = rs.getTimestamp("deleted_at");
    if (c != null) {
      f.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      f.setUpdatedAt(u.toLocalDateTime());
    }
    if (d != null) {
      f.setDeletedAt(d.toLocalDateTime());
    }
    return f;
  }
}
