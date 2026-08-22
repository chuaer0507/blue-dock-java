package com.bluedock.search.engine;

import com.bluedock.search.repo.SearchDocsRepository;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 读 {@code bluedock_search_docs}。联系人仍走源表（尚未索引）；文件若索引为空由门面回退 mysql。
 */
@Component
public class DocsSearchEngine implements SearchEngine {
  private final SearchDocsRepository docs;
  private final MysqlLikeSearchEngine mysql;

  public DocsSearchEngine(SearchDocsRepository docs, MysqlLikeSearchEngine mysql) {
    this.docs = docs;
    this.mysql = mysql;
  }

  @Override
  public String name() {
    return "docs";
  }

  @Override
  public List<SearchHitView> contacts(String key, int limit) {
    List<SearchHitView> hits = docs.contacts(key, limit);
    return hits.isEmpty() ? mysql.contacts(key, limit) : hits;
  }

  @Override
  public List<SearchHitView> projects(long userId, String key, int limit) {
    return docs.projects(userId, key, limit);
  }

  @Override
  public List<SearchHitView> tasks(long userId, String key, int limit) {
    return docs.tasks(userId, key, limit);
  }

  @Override
  public List<SearchHitView> files(long userId, String key, int limit) {
    List<SearchHitView> hits = docs.files(userId, key, limit);
    return hits.isEmpty() ? mysql.files(userId, key, limit) : hits;
  }

  @Override
  public List<SearchHitView> messages(long userId, String key, int limit) {
    return docs.messages(userId, key, limit);
  }
}
