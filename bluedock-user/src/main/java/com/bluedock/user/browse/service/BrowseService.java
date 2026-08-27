package com.bluedock.user.browse.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.user.browse.repo.RecentItemRepository;
import com.bluedock.user.browse.repo.TaskBrowseRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrowseService {
  private final TaskBrowseRepository taskBrowses;
  private final RecentItemRepository recentItems;
  private final BrowseRecorder recorder;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public BrowseService(
      TaskBrowseRepository taskBrowses,
      RecentItemRepository recentItems,
      BrowseRecorder recorder,
      JdbcTemplate jdbc,
      ObjectMapper json) {
    this.taskBrowses = taskBrowses;
    this.recentItems = recentItems;
    this.recorder = recorder;
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Map<String, Object>> taskBrowse(Integer limit) {
    long userId = AuthContext.requireUserId();
    int lim = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
    return taskBrowses.list(userId, lim);
  }

  @Transactional
  public Map<String, Object> taskBrowseSave(Long taskId) {
    long userId = AuthContext.requireUserId();
    if (taskId == null || taskId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BROWSE_PARAM_INVALID);
    }
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_tasks WHERE id = ? AND deleted_at IS NULL",
            Integer.class,
            taskId);
    if (n == null || n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND);
    }
    recorder.recordTask(userId, taskId);
    return Map.of();
  }

  @Transactional
  public Map<String, Object> taskBrowseClean(Integer keepCount) {
    long userId = AuthContext.requireUserId();
    int keep = keepCount == null ? 100 : Math.max(keepCount, 0);
    int deleted = taskBrowses.clean(userId, keep);
    return Map.of("deletedCount", deleted);
  }

  public Map<String, Object> recentBrowse(String type, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
    String t = type == null || type.isBlank() ? null : type.trim();
    int total = recentItems.count(userId, t);
    List<Map<String, Object>> rows = recentItems.page(userId, t, (p - 1) * size, size);
    List<Map<String, Object>> list = enrich(rows);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("page", p);
    out.put("pageSize", size);
    out.put("total", total);
    return out;
  }

  @Transactional
  public Map<String, Object> recentDelete(Long id) {
    long userId = AuthContext.requireUserId();
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BROWSE_PARAM_INVALID);
    }
    if (recentItems.findOwned(userId, id).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.BROWSE_NOT_FOUND);
    }
    recentItems.delete(userId, id);
    return Map.of();
  }

  private List<Map<String, Object>> enrich(List<Map<String, Object>> rows) {
    Set<Long> taskIds =
        rows.stream()
            .filter(r -> JdbcBrowseRecorder.TYPE_TASK.equals(r.get("targetType")))
            .map(r -> (Long) r.get("targetId"))
            .collect(Collectors.toSet());
    Set<Long> fileIds =
        rows.stream()
            .filter(r -> JdbcBrowseRecorder.TYPE_FILE.equals(r.get("targetType")))
            .map(r -> (Long) r.get("targetId"))
            .collect(Collectors.toSet());
    Set<Long> taskFileIds =
        rows.stream()
            .filter(r -> JdbcBrowseRecorder.TYPE_TASK_FILE.equals(r.get("targetType")))
            .map(r -> (Long) r.get("targetId"))
            .collect(Collectors.toSet());
    Set<Long> messageIds =
        rows.stream()
            .filter(r -> "message_file".equals(r.get("targetType")))
            .map(r -> (Long) r.get("targetId"))
            .collect(Collectors.toSet());

    Map<Long, Map<String, Object>> tasks = loadTasks(taskIds);
    Map<Long, Map<String, Object>> files = loadFiles(fileIds);
    Map<Long, Map<String, Object>> taskFiles = loadTaskFiles(taskFileIds);
    Map<Long, Map<String, Object>> messageFiles = loadMessageFiles(messageIds);

    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String targetType = String.valueOf(row.get("targetType"));
      long targetId = (Long) row.get("targetId");
      Map<String, Object> base = new LinkedHashMap<>();
      base.put("recordId", row.get("id"));
      base.put("sourceType", row.get("sourceType"));
      base.put("sourceId", row.get("sourceId"));
      base.put("browsedAt", row.get("browsedAt"));

      switch (targetType) {
        case JdbcBrowseRecorder.TYPE_TASK -> {
          Map<String, Object> task = tasks.get(targetId);
          if (task == null) {
            continue;
          }
          base.putAll(task);
          base.put("type", JdbcBrowseRecorder.TYPE_TASK);
          out.add(base);
        }
        case JdbcBrowseRecorder.TYPE_FILE -> {
          Map<String, Object> file = files.get(targetId);
          if (file == null) {
            continue;
          }
          base.putAll(file);
          base.put("type", JdbcBrowseRecorder.TYPE_FILE);
          out.add(base);
        }
        case JdbcBrowseRecorder.TYPE_TASK_FILE -> {
          Map<String, Object> tf = taskFiles.get(targetId);
          if (tf == null) {
            continue;
          }
          base.putAll(tf);
          base.put("type", JdbcBrowseRecorder.TYPE_TASK_FILE);
          out.add(base);
        }
        case "message_file" -> {
          Map<String, Object> messageFile = messageFiles.get(targetId);
          if (messageFile == null) {
            continue;
          }
          base.putAll(messageFile);
          base.put("type", "message_file");
          out.add(base);
        }
        default -> {
          // ignore unknown
        }
      }
    }
    return out;
  }

  private Map<Long, Map<String, Object>> loadTaskFiles(Set<Long> ids) {
    Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
    Object[] args = ids.toArray();
    jdbc.query(
        """
        SELECT f.id, f.name, f.extension, f.size, f.task_id, f.project_id,
               t.name AS task_name, p.name AS project_name
        FROM bluedock_task_files f
        LEFT JOIN bluedock_tasks t ON t.id = f.task_id
        LEFT JOIN bluedock_projects p ON p.id = f.project_id
        WHERE f.id IN (%s) AND f.deleted_at IS NULL
        """
            .formatted(placeholders),
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          long id = rs.getLong("id");
          m.put("id", id);
          m.put("name", rs.getString("name"));
          m.put("extension", rs.getString("extension") == null ? "" : rs.getString("extension"));
          m.put("size", rs.getLong("size"));
          m.put("taskId", rs.getLong("task_id"));
          m.put("taskName", rs.getString("task_name") == null ? "" : rs.getString("task_name"));
          m.put("projectId", rs.getLong("project_id"));
          m.put(
              "projectName",
              rs.getString("project_name") == null ? "" : rs.getString("project_name"));
          map.put(id, m);
          return null;
        },
        args);
    return map;
  }

  private Map<Long, Map<String, Object>> loadTasks(Set<Long> ids) {
    Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
    Object[] args = ids.toArray();
    jdbc.query(
        """
        SELECT t.id, t.name, t.project_id, t.column_id, t.complete_at, p.name AS project_name
        FROM bluedock_tasks t
        LEFT JOIN bluedock_projects p ON p.id = t.project_id
        WHERE t.id IN (%s) AND t.deleted_at IS NULL AND t.archived_at IS NULL
        """
            .formatted(placeholders),
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          long id = rs.getLong("id");
          m.put("id", id);
          m.put("name", rs.getString("name"));
          m.put("projectId", rs.getLong("project_id"));
          m.put(
              "projectName",
              rs.getString("project_name") == null ? "" : rs.getString("project_name"));
          m.put("columnId", rs.getLong("column_id"));
          var c = rs.getTimestamp("complete_at");
          m.put("completeAt", c == null ? null : c.toLocalDateTime().toString());
          map.put(id, m);
          return null;
        },
        args);
    return map;
  }

  private Map<Long, Map<String, Object>> loadFiles(Set<Long> ids) {
    Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
    Object[] args = ids.toArray();
    jdbc.query(
        """
        SELECT id, name, extension, size, type, parent_id
        FROM bluedock_files
        WHERE id IN (%s) AND deleted_at IS NULL
        """
            .formatted(placeholders),
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          long id = rs.getLong("id");
          m.put("id", id);
          m.put("name", rs.getString("name"));
          m.put("extension", rs.getString("extension") == null ? "" : rs.getString("extension"));
          m.put("size", rs.getLong("size"));
          m.put("fileType", rs.getString("type"));
          m.put("folderId", rs.getLong("parent_id"));
          map.put(id, m);
          return null;
        },
        args);
    return map;
  }

  private Map<Long, Map<String, Object>> loadMessageFiles(Set<Long> ids) {
    Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
    if (ids.isEmpty()) {
      return map;
    }
    String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
    Object[] args = ids.toArray();
    jdbc.query(
        """
        SELECT m.id, m.dialog_id, m.body, m.type, d.name AS dialog_name
        FROM bluedock_dialog_messages m
        LEFT JOIN bluedock_dialogs d ON d.id = m.dialog_id
        WHERE m.id IN (%s) AND m.deleted_at IS NULL AND m.type = 'file'
        """
            .formatted(placeholders),
        (rs, i) -> {
          long id = rs.getLong("id");
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", id);
          m.put("dialogId", rs.getLong("dialog_id"));
          m.put(
              "dialogName",
              rs.getString("dialog_name") == null ? "" : rs.getString("dialog_name"));
          String raw = rs.getString("body");
          m.put("name", "");
          m.put("extension", "");
          m.put("size", 0L);
          if (raw != null && !raw.isBlank()) {
            try {
              JsonNode node = json.readTree(raw);
              if (node.hasNonNull("name")) {
                m.put("name", node.get("name").asText(""));
              }
              if (node.hasNonNull("extension")) {
                m.put("extension", node.get("extension").asText(""));
              }
              if (node.hasNonNull("size")) {
                m.put("size", node.get("size").asLong(0L));
              }
            } catch (Exception ignored) {
              // keep defaults
            }
          }
          map.put(id, m);
          return null;
        },
        args);
    return map;
  }
}
