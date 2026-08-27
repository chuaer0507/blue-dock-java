package com.bluedock.search.engine;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.search.config.SearchProperties;
import com.bluedock.search.web.dto.SearchHitView;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** OpenSearch HTTP 检索（可选）；权限用用户项目 / 会话成员约束。 */
@Component
public class OpenSearchEngine implements SearchEngine {
  private static final Logger log = LoggerFactory.getLogger(OpenSearchEngine.class);

  private final SearchProperties props;
  private final ObjectMapper objectMapper;
  private final MysqlLikeSearchEngine mysql;
  private final JdbcTemplate jdbc;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public OpenSearchEngine(
      SearchProperties props,
      ObjectMapper objectMapper,
      MysqlLikeSearchEngine mysql,
      JdbcTemplate jdbc) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.mysql = mysql;
    this.jdbc = jdbc;
  }

  public boolean available() {
    if (!props.getOpensearch().isEnabled()) {
      return false;
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(trimSlash(props.getOpensearch().getUrl()) + "/"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      return resp.statusCode() >= 200 && resp.statusCode() < 300;
    } catch (Exception e) {
      log.debug("opensearch ping failed: {}", e.toString());
      return false;
    }
  }

  @Override
  public String name() {
    return "opensearch";
  }

  @Override
  public List<SearchHitView> contacts(String key, int limit) {
    return mysql.contacts(key, limit);
  }

  @Override
  public List<SearchHitView> projects(long userId, String key, int limit) {
    List<Long> ids = userProjectIds(userId);
    if (ids.isEmpty()) {
      return List.of();
    }
    return search(SearchIndexEvent.TYPE_PROJECT, key, limit, Map.of("refId", ids));
  }

  @Override
  public List<SearchHitView> tasks(long userId, String key, int limit) {
    List<Long> ids = userProjectIds(userId);
    if (ids.isEmpty()) {
      return List.of();
    }
    return search(SearchIndexEvent.TYPE_TASK, key, limit, Map.of("projectId", ids));
  }

  @Override
  public List<SearchHitView> files(long userId, String key, int limit) {
    return search(SearchIndexEvent.TYPE_FILE, key, limit, Map.of("userId", List.of(userId)));
  }

  @Override
  public List<SearchHitView> messages(long userId, String key, int limit) {
    List<Long> dialogIds = userDialogIds(userId);
    if (dialogIds.isEmpty()) {
      return List.of();
    }
    // message docs store projectId unused; use userId of sender is wrong.
    // Index content only; filter by loading hits then membership — use terms on refId via post-filter.
    List<SearchHitView> raw = search(SearchIndexEvent.TYPE_MESSAGE, key, Math.min(limit * 3, 50), Map.of());
    if (raw.isEmpty()) {
      return raw;
    }
    // keep only messages in user's dialogs
    String placeholders = String.join(",", dialogIds.stream().map(x -> "?").toList());
    List<Long> allowed =
        jdbc.query(
            """
            SELECT id FROM bluedock_dialog_messages
            WHERE deleted_at IS NULL AND dialog_id IN (%s)
            """
                .formatted(placeholders),
            (rs, i) -> rs.getLong(1),
            dialogIds.toArray());
    var allow = java.util.Set.copyOf(allowed);
    return raw.stream().filter(h -> allow.contains(h.id())).limit(limit).toList();
  }

  private List<Long> userProjectIds(long userId) {
    return jdbc.query(
        """
        SELECT project_id FROM bluedock_project_users WHERE user_id = ?
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  private List<Long> userDialogIds(long userId) {
    return jdbc.query(
        "SELECT dialog_id FROM bluedock_dialog_users WHERE user_id = ?",
        (rs, i) -> rs.getLong(1),
        userId);
  }

  private List<SearchHitView> search(
      String docType, String key, int limit, Map<String, Object> termsFilters) {
    try {
      Map<String, Object> bool = new LinkedHashMap<>();
      List<Object> must = new ArrayList<>();
      must.add(Map.of("term", Map.of("docType", docType)));
      must.add(
          Map.of(
              "multi_match",
              Map.of("query", key, "fields", List.of("title", "content"), "type", "best_fields")));
      for (Map.Entry<String, Object> e : termsFilters.entrySet()) {
        Object v = e.getValue();
        if (v instanceof List<?> list) {
          if (list.isEmpty()) {
            return List.of();
          }
          must.add(Map.of("terms", Map.of(e.getKey(), list)));
        } else {
          must.add(Map.of("term", Map.of(e.getKey(), v)));
        }
      }
      bool.put("must", must);
      Map<String, Object> body = Map.of("size", limit, "query", Map.of("bool", bool));
      String json = objectMapper.writeValueAsString(body);
      String url =
          trimSlash(props.getOpensearch().getUrl())
              + "/"
              + props.getOpensearch().getIndex()
              + "/_search";
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() >= 300) {
        throw new IllegalStateException("status=" + resp.statusCode() + " body=" + resp.body());
      }
      return parseHits(docType, resp.body());
    } catch (Exception e) {
      throw new IllegalStateException("opensearch search failed: " + e, e);
    }
  }

  private List<SearchHitView> parseHits(String docType, String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    JsonNode hits = root.path("hits").path("hits");
    List<SearchHitView> out = new ArrayList<>();
    if (!hits.isArray()) {
      return out;
    }
    for (JsonNode h : hits) {
      JsonNode src = h.path("_source");
      long refId = src.path("refId").asLong(0);
      String title = src.path("title").asString("");
      String content = src.path("content").asString("");
      long projectId = src.path("projectId").asLong(0);
      if (SearchIndexEvent.TYPE_MESSAGE.equals(docType)) {
        String snippet = content.isEmpty() ? title : content;
        if (snippet.length() > 120) {
          snippet = snippet.substring(0, 120);
        }
        out.add(new SearchHitView(docType, refId, snippet, snippet, 0L));
      } else if (SearchIndexEvent.TYPE_PROJECT.equals(docType)) {
        out.add(new SearchHitView(docType, refId, title, content, refId));
      } else {
        out.add(new SearchHitView(docType, refId, title, content, projectId));
      }
    }
    return out;
  }

  private static String trimSlash(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
