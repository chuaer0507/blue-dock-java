package com.bluedock.search.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.search.engine.SearchEngine;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
  private final SearchEngine search;

  public SearchService(SearchEngine search) {
    this.search = search;
  }

  public List<SearchHitView> contact(String key, Integer take) {
    return search.contacts(requireKey(key), limit(take));
  }

  public List<SearchHitView> project(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    return search.projects(userId, requireKey(key), limit(take));
  }

  public List<SearchHitView> task(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    return search.tasks(userId, requireKey(key), limit(take));
  }

  public List<SearchHitView> file(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    return search.files(userId, requireKey(key), limit(take));
  }

  public List<SearchHitView> message(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    return search.messages(userId, requireKey(key), limit(take));
  }

  private static String requireKey(String key) {
    String k = key == null ? "" : key.trim();
    if (k.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SEARCH_KEY_REQUIRED);
    }
    if (k.length() > 100) {
      k = k.substring(0, 100);
    }
    return k;
  }

  private static int limit(Integer take) {
    if (take == null) {
      return 20;
    }
    return Math.min(50, Math.max(1, take));
  }
}
