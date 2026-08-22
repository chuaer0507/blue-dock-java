package com.bluedock.system.upload;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UploadObjectRepository {
  private static final RowMapper<UploadObject> MAPPER =
      (rs, i) -> {
        UploadObject o = new UploadObject();
        o.setId(rs.getLong("id"));
        o.setObjectKey(rs.getString("object_key"));
        o.setUrl(rs.getString("url"));
        o.setCategory(rs.getString("category"));
        o.setOriginalName(rs.getString("original_name"));
        o.setContentType(rs.getString("content_type"));
        o.setSizeBytes(rs.getLong("size_bytes"));
        o.setProvider(rs.getString("provider"));
        long uploader = rs.getLong("uploader_id");
        o.setUploaderId(rs.wasNull() ? null : uploader);
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) {
          o.setCreatedAt(c.toLocalDateTime());
        }
        Timestamp d = rs.getTimestamp("deleted_at");
        if (d != null) {
          o.setDeletedAt(d.toLocalDateTime());
        }
        return o;
      };

  private final JdbcTemplate jdbc;

  public UploadObjectRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(UploadObject o) {
    jdbc.update(
        """
        INSERT INTO bluedock_upload_objects
          (id, object_key, url, category, original_name, content_type, size_bytes, provider,
           uploader_id, created_at, deleted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
        """,
        o.getId(),
        o.getObjectKey(),
        o.getUrl(),
        o.getCategory(),
        o.getOriginalName(),
        o.getContentType(),
        o.getSizeBytes(),
        o.getProvider(),
        o.getUploaderId(),
        Timestamp.valueOf(o.getCreatedAt() == null ? LocalDateTime.now() : o.getCreatedAt()));
  }

  public Optional<UploadObject> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT * FROM bluedock_upload_objects
            WHERE id = ? AND deleted_at IS NULL
            LIMIT 1
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public long count(String category, String q) {
    StringBuilder sql =
        new StringBuilder("SELECT COUNT(*) FROM bluedock_upload_objects WHERE deleted_at IS NULL");
    List<Object> args = new ArrayList<>();
    appendFilters(sql, args, category, q);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  public List<UploadObject> page(String category, String q, int offset, int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT * FROM bluedock_upload_objects
            WHERE deleted_at IS NULL
            """);
    List<Object> args = new ArrayList<>();
    appendFilters(sql, args, category, q);
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  /** 本人图片空间：按上传者 + 分类，可选 object_key 前缀。 */
  public List<UploadObject> pageByUploader(
      long uploaderId, String category, String keyPrefix, int offset, int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT * FROM bluedock_upload_objects
            WHERE deleted_at IS NULL AND uploader_id = ?
            """);
    List<Object> args = new ArrayList<>();
    args.add(uploaderId);
    appendUploaderFilters(sql, args, category, keyPrefix);
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  public long countByUploader(long uploaderId, String category, String keyPrefix) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT COUNT(*) FROM bluedock_upload_objects WHERE deleted_at IS NULL AND uploader_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(uploaderId);
    appendUploaderFilters(sql, args, category, keyPrefix);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  public void softDelete(long id) {
    jdbc.update(
        """
        UPDATE bluedock_upload_objects
        SET deleted_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  private static void appendFilters(
      StringBuilder sql, List<Object> args, String category, String q) {
    if (category != null && !category.isBlank()) {
      sql.append(" AND category = ?");
      args.add(category.trim());
    }
    if (q != null && !q.isBlank()) {
      String like = "%" + q.trim() + "%";
      sql.append(" AND (original_name LIKE ? OR object_key LIKE ?)");
      args.add(like);
      args.add(like);
    }
  }

  private static void appendUploaderFilters(
      StringBuilder sql, List<Object> args, String category, String keyPrefix) {
    if (category != null && !category.isBlank()) {
      sql.append(" AND category = ?");
      args.add(category.trim());
    }
    if (keyPrefix != null && !keyPrefix.isBlank()) {
      sql.append(" AND object_key LIKE ?");
      args.add(keyPrefix.trim() + "%");
    }
  }
}
