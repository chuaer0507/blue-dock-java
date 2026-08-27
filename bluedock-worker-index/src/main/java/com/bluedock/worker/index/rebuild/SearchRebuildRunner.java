package com.bluedock.worker.index.rebuild;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.worker.index.opensearch.OpenSearchIndexSink;
import com.bluedock.worker.index.repo.SearchDocRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 扫源表回填 {@code bluedock_search_docs}（及可选 OpenSearch）。 */
@Component
public class SearchRebuildRunner {
  private static final Logger log = LoggerFactory.getLogger(SearchRebuildRunner.class);
  private static final int BATCH = 200;

  private final JdbcTemplate jdbc;
  private final SearchDocRepository docs;
  private final OpenSearchIndexSink opensearch;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public SearchRebuildRunner(
      JdbcTemplate jdbc,
      SearchDocRepository docs,
      OpenSearchIndexSink opensearch,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.docs = docs;
    this.opensearch = opensearch;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void run(String eventId, String typesCsv) {
    List<String> types = parseTypes(typesCsv);
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("state", "running");
    status.put("eventId", eventId);
    status.put("types", types);
    status.put("startedAt", System.currentTimeMillis());
    writeStatus(status);
    Map<String, Integer> counts = new LinkedHashMap<>();
    try {
      for (String type : types) {
        int n =
            switch (type) {
              case SearchIndexEvent.TYPE_CONTACT -> rebuildContacts(eventId);
              case SearchIndexEvent.TYPE_PROJECT -> rebuildProjects(eventId);
              case SearchIndexEvent.TYPE_TASK -> rebuildTasks(eventId);
              case SearchIndexEvent.TYPE_FILE -> rebuildFiles(eventId);
              case SearchIndexEvent.TYPE_MESSAGE -> rebuildMessages(eventId);
              default -> 0;
            };
        counts.put(type, n);
        status.put("counts", counts);
        writeStatus(status);
      }
      status.put("state", "done");
      status.put("finishedAt", System.currentTimeMillis());
      status.put("counts", counts);
      writeStatus(status);
      log.info("search rebuild done eventId={} counts={}", eventId, counts);
    } catch (Exception e) {
      status.put("state", "failed");
      status.put("error", e.toString());
      status.put("finishedAt", System.currentTimeMillis());
      writeStatus(status);
      log.warn("search rebuild failed eventId={}: {}", eventId, e.toString());
    } finally {
      redis.delete(RedisKeys.searchRebuildLock());
    }
  }

  private int rebuildContacts(String eventId) {
    docs.deleteByType(SearchIndexEvent.TYPE_CONTACT);
    int total = 0;
    long afterId = 0;
    while (true) {
      List<Row> batch =
          jdbc.query(
              """
              SELECT id, nickname, email FROM bluedock_users
              WHERE id > ? AND disable_at IS NULL AND IFNULL(is_bot,0) = 0
              ORDER BY id ASC LIMIT ?
              """,
              (rs, i) ->
                  new Row(
                      rs.getLong("id"),
                      rs.getLong("id"),
                      0L,
                      nullToEmpty(rs.getString("nickname")),
                      nullToEmpty(rs.getString("email"))),
              afterId,
              BATCH);
      if (batch.isEmpty()) {
        break;
      }
      for (Row r : batch) {
        upsert(SearchIndexEvent.TYPE_CONTACT, r, eventId);
        afterId = r.refId();
        total++;
      }
    }
    return total;
  }

  private int rebuildProjects(String eventId) {
    docs.deleteByType(SearchIndexEvent.TYPE_PROJECT);
    int total = 0;
    long afterId = 0;
    while (true) {
      List<Row> batch =
          jdbc.query(
              """
              SELECT id, user_id, name, description FROM bluedock_projects
              WHERE id > ? AND deleted_at IS NULL AND archived_at IS NULL
              ORDER BY id ASC LIMIT ?
              """,
              (rs, i) ->
                  new Row(
                      rs.getLong("id"),
                      rs.getLong("user_id"),
                      rs.getLong("id"),
                      nullToEmpty(rs.getString("name")),
                      nullToEmpty(rs.getString("description"))),
              afterId,
              BATCH);
      if (batch.isEmpty()) {
        break;
      }
      for (Row r : batch) {
        upsert(SearchIndexEvent.TYPE_PROJECT, r, eventId);
        afterId = r.refId();
        total++;
      }
    }
    return total;
  }

  private int rebuildTasks(String eventId) {
    docs.deleteByType(SearchIndexEvent.TYPE_TASK);
    int total = 0;
    long afterId = 0;
    while (true) {
      List<Row> batch =
          jdbc.query(
              """
              SELECT id, user_id, project_id, name, description FROM bluedock_tasks
              WHERE id > ? AND deleted_at IS NULL AND archived_at IS NULL AND parent_id = 0
              ORDER BY id ASC LIMIT ?
              """,
              (rs, i) ->
                  new Row(
                      rs.getLong("id"),
                      rs.getLong("user_id"),
                      rs.getLong("project_id"),
                      nullToEmpty(rs.getString("name")),
                      nullToEmpty(rs.getString("description"))),
              afterId,
              BATCH);
      if (batch.isEmpty()) {
        break;
      }
      for (Row r : batch) {
        upsert(SearchIndexEvent.TYPE_TASK, r, eventId);
        afterId = r.refId();
        total++;
      }
    }
    return total;
  }

  private int rebuildFiles(String eventId) {
    docs.deleteByType(SearchIndexEvent.TYPE_FILE);
    int total = 0;
    long afterId = 0;
    while (true) {
      List<Row> batch =
          jdbc.query(
              """
              SELECT id, user_id, name, type FROM bluedock_files
              WHERE id > ? AND deleted_at IS NULL
              ORDER BY id ASC LIMIT ?
              """,
              (rs, i) ->
                  new Row(
                      rs.getLong("id"),
                      rs.getLong("user_id"),
                      0L,
                      nullToEmpty(rs.getString("name")),
                      nullToEmpty(rs.getString("type"))),
              afterId,
              BATCH);
      if (batch.isEmpty()) {
        break;
      }
      for (Row r : batch) {
        upsert(SearchIndexEvent.TYPE_FILE, r, eventId);
        afterId = r.refId();
        total++;
      }
    }
    return total;
  }

  private int rebuildMessages(String eventId) {
    docs.deleteByType(SearchIndexEvent.TYPE_MESSAGE);
    int total = 0;
    long afterId = 0;
    while (true) {
      List<Row> batch =
          jdbc.query(
              """
              SELECT id, user_id, body FROM bluedock_dialog_messages
              WHERE id > ? AND deleted_at IS NULL AND type = 'text'
              ORDER BY id ASC LIMIT ?
              """,
              (rs, i) -> {
                String body = nullToEmpty(rs.getString("body"));
                String title = body.length() > 80 ? body.substring(0, 80) : body;
                return new Row(rs.getLong("id"), rs.getLong("user_id"), 0L, title, body);
              },
              afterId,
              BATCH);
      if (batch.isEmpty()) {
        break;
      }
      for (Row r : batch) {
        upsert(SearchIndexEvent.TYPE_MESSAGE, r, eventId);
        afterId = r.refId();
        total++;
      }
    }
    return total;
  }

  private void upsert(String docType, Row r, String eventId) {
    docs.upsert(docType, r.refId(), r.userId(), r.projectId(), r.title(), r.content(), eventId);
    opensearch.upsert(
        new SearchIndexEvent(
            eventId + "-" + docType + "-" + r.refId(),
            SearchIndexEvent.ACTION_UPSERT,
            docType,
            r.refId(),
            r.userId(),
            r.projectId(),
            r.title(),
            r.content()));
  }

  private void writeStatus(Map<String, Object> status) {
    try {
      redis
          .opsForValue()
          .set(
              RedisKeys.searchRebuildStatus(),
              objectMapper.writeValueAsString(status),
              Duration.ofHours(24));
    } catch (Exception ignored) {
      // best-effort
    }
  }

  private static List<String> parseTypes(String typesCsv) {
    List<String> all =
        List.of(
            SearchIndexEvent.TYPE_CONTACT,
            SearchIndexEvent.TYPE_PROJECT,
            SearchIndexEvent.TYPE_TASK,
            SearchIndexEvent.TYPE_FILE,
            SearchIndexEvent.TYPE_MESSAGE);
    if (typesCsv == null || typesCsv.isBlank()) {
      return all;
    }
    List<String> out = new ArrayList<>();
    for (String part : typesCsv.split("[,\\s]+")) {
      String t = part.trim().toLowerCase(Locale.ROOT);
      if (all.contains(t) && !out.contains(t)) {
        out.add(t);
      }
    }
    return out.isEmpty() ? all : out;
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private record Row(long refId, long userId, long projectId, String title, String content) {}
}
