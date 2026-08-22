package com.bluedock.user.favorite.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.user.favorite.repo.UserFavoriteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserFavoriteService {
  private static final Set<String> TYPES = Set.of("task", "project", "file", "message");

  private final UserFavoriteRepository favorites;
  private final JdbcTemplate jdbc;

  public UserFavoriteService(UserFavoriteRepository favorites, JdbcTemplate jdbc) {
    this.favorites = favorites;
    this.jdbc = jdbc;
  }

  public Map<String, Object> list(String type, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    String t = normalizeType(type, true);
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
    int offset = (p - 1) * size;
    List<Map<String, Object>> list = favorites.page(userId, t, offset, size);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("total", favorites.count(userId, t));
    out.put("page", p);
    out.put("pageSize", size);
    return out;
  }

  @Transactional
  public Map<String, Object> toggle(String type, Long id) {
    long userId = AuthContext.requireUserId();
    String t = normalizeType(type, false);
    long refId = id == null ? 0 : id;
    if (refId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_PARAM_INVALID);
    }
    assertExists(t, refId);
    var exist = favorites.findId(userId, t, refId);
    if (exist.isPresent()) {
      favorites.delete(userId, t, refId);
      return Map.of("favorited", false, "type", t, "id", refId);
    }
    favorites.insert(userId, t, refId);
    return Map.of("favorited", true, "type", t, "id", refId);
  }

  @Transactional
  public Map<String, Object> remark(String type, Long id, String remark) {
    long userId = AuthContext.requireUserId();
    String t = normalizeType(type, false);
    long refId = id == null ? 0 : id;
    String r = remark == null ? "" : remark.trim();
    if (refId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_PARAM_INVALID);
    }
    if (r.isEmpty() || r.length() > 255) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_REMARK_INVALID);
    }
    if (favorites.findId(userId, t, refId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FAVORITE_NOT_FOUND);
    }
    favorites.updateRemark(userId, t, refId, r);
    return Map.of("remark", r);
  }

  @Transactional
  public Map<String, Object> clean(String type) {
    long userId = AuthContext.requireUserId();
    String t = normalizeType(type, true);
    int n = favorites.deleteByUser(userId, t);
    return Map.of("deletedCount", n);
  }

  public Map<String, Object> check(String type, Long id) {
    long userId = AuthContext.requireUserId();
    String t = normalizeType(type, false);
    long refId = id == null ? 0 : id;
    if (refId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_PARAM_INVALID);
    }
    boolean favorited = favorites.findId(userId, t, refId).isPresent();
    return Map.of("favorited", favorited, "type", t, "id", refId);
  }

  private void assertExists(String type, long refId) {
    String sql =
        switch (type) {
          case "task" -> "SELECT COUNT(1) FROM bluedock_tasks WHERE id = ? AND deleted_at IS NULL";
          case "project" -> "SELECT COUNT(1) FROM bluedock_projects WHERE id = ? AND deleted_at IS NULL";
          case "file" -> "SELECT COUNT(1) FROM bluedock_files WHERE id = ? AND deleted_at IS NULL";
          case "message" ->
              "SELECT COUNT(1) FROM bluedock_dialog_messages WHERE id = ? AND deleted_at IS NULL";
          default -> throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_TYPE_INVALID);
        };
    Integer n = jdbc.queryForObject(sql, Integer.class, refId);
    if (n == null || n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FAVORITE_NOT_FOUND);
    }
  }

  private static String normalizeType(String type, boolean allowEmpty) {
    if (type == null || type.isBlank()) {
      if (allowEmpty) {
        return null;
      }
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_TYPE_INVALID);
    }
    String t = type.trim().toLowerCase();
    if (!TYPES.contains(t)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FAVORITE_TYPE_INVALID);
    }
    return t;
  }
}
