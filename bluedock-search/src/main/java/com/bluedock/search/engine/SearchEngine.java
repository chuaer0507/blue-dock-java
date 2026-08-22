package com.bluedock.search.engine;

import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;

/** 可切换检索后端。 */
public interface SearchEngine {
  String name();

  List<SearchHitView> contacts(String key, int limit);

  List<SearchHitView> projects(long userId, String key, int limit);

  List<SearchHitView> tasks(long userId, String key, int limit);

  List<SearchHitView> files(long userId, String key, int limit);

  List<SearchHitView> messages(long userId, String key, int limit);
}
