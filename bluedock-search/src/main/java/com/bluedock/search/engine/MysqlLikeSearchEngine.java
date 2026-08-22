package com.bluedock.search.engine;

import com.bluedock.search.repo.SearchQueryRepository;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.springframework.stereotype.Component;

/** 源表 MySQL LIKE（权限在 SQL 内）。 */
@Component
public class MysqlLikeSearchEngine implements SearchEngine {
  private final SearchQueryRepository repo;

  public MysqlLikeSearchEngine(SearchQueryRepository repo) {
    this.repo = repo;
  }

  @Override
  public String name() {
    return "mysql";
  }

  @Override
  public List<SearchHitView> contacts(String key, int limit) {
    return repo.contacts(key, limit);
  }

  @Override
  public List<SearchHitView> projects(long userId, String key, int limit) {
    return repo.projects(userId, key, limit);
  }

  @Override
  public List<SearchHitView> tasks(long userId, String key, int limit) {
    return repo.tasks(userId, key, limit);
  }

  @Override
  public List<SearchHitView> files(long userId, String key, int limit) {
    return repo.files(userId, key, limit);
  }

  @Override
  public List<SearchHitView> messages(long userId, String key, int limit) {
    return repo.messages(userId, key, limit);
  }
}
