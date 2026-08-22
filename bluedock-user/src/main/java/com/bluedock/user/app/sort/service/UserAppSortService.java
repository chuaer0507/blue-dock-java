package com.bluedock.user.app.sort.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.user.app.sort.repo.UserAppSortRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAppSortService {
  private final UserAppSortRepository sorts;
  private final ObjectMapper objectMapper;

  public UserAppSortService(UserAppSortRepository sorts, ObjectMapper objectMapper) {
    this.sorts = sorts;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> get() {
    long userId = AuthContext.requireUserId();
    String json = sorts.findSortsJson(userId).orElse(null);
    Map<String, List<String>> normalized = normalize(parse(json));
    return Map.of("sorts", normalized);
  }

  @Transactional
  public Map<String, Object> save(Object sortsObj) {
    long userId = AuthContext.requireUserId();
    Map<String, List<String>> normalized = normalize(sortsObj);
    try {
      sorts.upsert(userId, objectMapper.writeValueAsString(normalized));
    } catch (Exception e) {
      sorts.upsert(userId, "{\"base\":[],\"admin\":[]}");
    }
    return Map.of("sorts", normalized);
  }

  private Object parse(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private Map<String, List<String>> normalize(Object sortsObj) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    result.put("base", List.of());
    result.put("admin", List.of());
    if (!(sortsObj instanceof Map<?, ?> map)) {
      return result;
    }
    for (String group : List.of("base", "admin")) {
      Object raw = map.get(group);
      List<?> list = raw instanceof List<?> l ? l : List.of();
      Set<String> normalized = new LinkedHashSet<>();
      for (Object v : list) {
        if (!(v instanceof String s)) {
          continue;
        }
        String t = s.trim();
        if (!t.isEmpty()) {
          normalized.add(t);
        }
      }
      result.put(group, new ArrayList<>(normalized));
    }
    return result;
  }
}
