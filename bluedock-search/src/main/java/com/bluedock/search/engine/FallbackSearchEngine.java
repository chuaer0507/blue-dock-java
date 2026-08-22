package com.bluedock.search.engine;

import com.bluedock.search.config.SearchProperties;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 按 {@code bluedock.search.engine} 选择后端；失败或空结果时降级 docs → mysql。
 */
@Component
@Primary
public class FallbackSearchEngine implements SearchEngine {
  private static final Logger log = LoggerFactory.getLogger(FallbackSearchEngine.class);

  private final SearchProperties props;
  private final MysqlLikeSearchEngine mysql;
  private final DocsSearchEngine docs;
  private final OpenSearchEngine opensearch;

  public FallbackSearchEngine(
      SearchProperties props,
      MysqlLikeSearchEngine mysql,
      DocsSearchEngine docs,
      OpenSearchEngine opensearch) {
    this.props = props;
    this.mysql = mysql;
    this.docs = docs;
    this.opensearch = opensearch;
  }

  @Override
  public String name() {
    return "fallback:" + preferredName();
  }

  @Override
  public List<SearchHitView> contacts(String key, int limit) {
    return mysql.contacts(key, limit);
  }

  @Override
  public List<SearchHitView> projects(long userId, String key, int limit) {
    return withFallback(userId, key, limit, "project");
  }

  @Override
  public List<SearchHitView> tasks(long userId, String key, int limit) {
    return withFallback(userId, key, limit, "task");
  }

  @Override
  public List<SearchHitView> files(long userId, String key, int limit) {
    return withFallback(userId, key, limit, "file");
  }

  @Override
  public List<SearchHitView> messages(long userId, String key, int limit) {
    return withFallback(userId, key, limit, "message");
  }

  private List<SearchHitView> withFallback(long userId, String key, int limit, String kind) {
    SearchEngine primary = preferred();
    try {
      List<SearchHitView> hits = invoke(primary, kind, userId, key, limit);
      if (!hits.isEmpty() || "mysql".equals(primary.name())) {
        return hits;
      }
    } catch (Exception e) {
      log.warn("search engine={} kind={} failed: {}", primary.name(), kind, e.toString());
    }
    if (!"docs".equals(primary.name())) {
      try {
        List<SearchHitView> hits = invoke(docs, kind, userId, key, limit);
        if (!hits.isEmpty()) {
          return hits;
        }
      } catch (Exception e) {
        log.warn("search docs fallback kind={} failed: {}", kind, e.toString());
      }
    }
    return invoke(mysql, kind, userId, key, limit);
  }

  private SearchEngine preferred() {
    String e = preferredName();
    return switch (e) {
      case "mysql" -> mysql;
      case "opensearch" -> opensearch.available() ? opensearch : docs;
      default -> docs;
    };
  }

  private String preferredName() {
    String e = props.getEngine() == null ? "docs" : props.getEngine().trim().toLowerCase(Locale.ROOT);
    if (e.isBlank()) {
      return "docs";
    }
    return e;
  }

  private static List<SearchHitView> invoke(
      SearchEngine engine, String kind, long userId, String key, int limit) {
    return switch (kind) {
      case "project" -> engine.projects(userId, key, limit);
      case "task" -> engine.tasks(userId, key, limit);
      case "file" -> engine.files(userId, key, limit);
      case "message" -> engine.messages(userId, key, limit);
      default -> List.of();
    };
  }
}
