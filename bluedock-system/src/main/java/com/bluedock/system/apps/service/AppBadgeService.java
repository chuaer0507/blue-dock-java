package com.bluedock.system.apps.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.system.apps.domain.InstalledApp;
import com.bluedock.system.apps.repo.AppBadgeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppBadgeService {
  private final AppBadgeRepository badges;
  private final InstalledAppService installedApps;
  private final RealtimeFanoutPublisher fanout;

  public AppBadgeService(
      AppBadgeRepository badges, InstalledAppService installedApps, RealtimeFanoutPublisher fanout) {
    this.badges = badges;
    this.installedApps = installedApps;
    this.fanout = fanout;
  }

  @Transactional
  public Map<String, Object> set(
      String appId, String secret, Object userId, String menuKey, Object countObj, Object dotObj) {
    String id = trim(appId);
    if (id.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    String sec = trim(secret);
    if (sec.isEmpty()) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.APPS_SECRET_INVALID);
    }
    String expect =
        installedApps
            .secretOf(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.APPS_NOT_INSTALLED));
    if (!expect.equals(sec)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.APPS_SECRET_INVALID);
    }
    String key = resolveMenuKey(id, menuKey);
    List<Long> userIds = normalizeUserIds(userId);
    if (userIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    int count = Math.max(0, toInt(countObj, 0));
    boolean dot = toBool(dotObj, false);
    if (count == 0 && !dot) {
      for (Long targetUserId : userIds) {
        badges.delete(id, key, targetUserId);
      }
    } else {
      for (Long targetUserId : userIds) {
        badges.upsert(id, key, targetUserId, count, dot);
      }
    }
    push(id, key, userIds, count, dot);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("appId", id);
    out.put("menuKey", key);
    out.put("count", count);
    out.put("dot", dot);
    out.put("affected", userIds.size());
    return out;
  }

  @Transactional
  public Map<String, Object> clear(String appId, String menuKey) {
    long userId = AuthContext.requireUserId();
    String id = trim(appId);
    if (id.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APPS_PARAM_INVALID);
    }
    if (!installedApps.isInstalled(id)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.APPS_NOT_INSTALLED);
    }
    String key = resolveMenuKey(id, menuKey);
    badges.delete(id, key, userId);
    push(id, key, List.of(userId), 0, false);
    return Map.of("appId", id, "menuKey", key);
  }

  public Map<String, Map<String, Map<String, Object>>> list() {
    long userId = AuthContext.requireUserId();
    Map<String, Map<String, Map<String, Object>>> map = new LinkedHashMap<>();
    for (Map<String, Object> row : badges.listByUser(userId)) {
      String appId = String.valueOf(row.get("appId"));
      if (!installedApps.isInstalled(appId)) {
        continue;
      }
      String menuKey = String.valueOf(row.get("menuKey"));
      map.computeIfAbsent(appId, k -> new LinkedHashMap<>())
          .put(
              menuKey,
              Map.of(
                  "count", row.get("count"),
                  "dot", row.get("dot")));
    }
    return map;
  }

  private String resolveMenuKey(String appId, String menuKeyInput) {
    String input = trim(menuKeyInput);
    if (installedApps.customAppIds().contains(appId)) {
      return input;
    }
    InstalledApp app =
        installedApps
            .findInstalled(appId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.APPS_NOT_INSTALLED));
    List<Map<String, Object>> menus = app.getMenus() == null ? List.of() : app.getMenus();
    if (menus.isEmpty()) {
      return input;
    }
    if (input.isEmpty()) {
      Object key = menus.get(0).get("key");
      return key == null ? "" : String.valueOf(key);
    }
    for (Map<String, Object> menu : menus) {
      String key = menu.get("key") == null ? "" : String.valueOf(menu.get("key"));
      if (input.equals(key)) {
        return key;
      }
    }
    throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.APPS_MENU_MISSING);
  }

  private void push(String appId, String menuKey, List<Long> userIds, int count, boolean dot) {
    if (userIds.isEmpty()) {
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("appId", appId);
    data.put("menuKey", menuKey);
    data.put("count", count);
    data.put("dot", dot);
    fanout.publish(
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""),
            RealtimeEventTypes.APP_BADGE,
            List.copyOf(userIds),
            data));
  }

  private static List<Long> normalizeUserIds(Object userId) {
    Set<Long> ids = new LinkedHashSet<>();
    if (userId instanceof Collection<?> col) {
      for (Object o : col) {
        addUserId(ids, o);
      }
    } else {
      addUserId(ids, userId);
    }
    return new ArrayList<>(ids);
  }

  private static void addUserId(Set<Long> ids, Object o) {
    if (o == null) {
      return;
    }
    if (o instanceof Number n) {
      long v = n.longValue();
      if (v > 0) {
        ids.add(v);
      }
      return;
    }
    try {
      long v = Long.parseLong(String.valueOf(o).trim());
      if (v > 0) {
        ids.add(v);
      }
    } catch (NumberFormatException ignored) {
      // skip
    }
  }

  private static int toInt(Object o, int def) {
    if (o instanceof Number n) {
      return n.intValue();
    }
    if (o == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(o).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static boolean toBool(Object o, boolean def) {
    if (o instanceof Boolean b) {
      return b;
    }
    if (o == null) {
      return def;
    }
    String s = String.valueOf(o).toLowerCase();
    if ("true".equals(s) || "1".equals(s)) {
      return true;
    }
    if ("false".equals(s) || "0".equals(s)) {
      return false;
    }
    return def;
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
