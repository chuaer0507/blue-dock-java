package com.bluedock.user.device.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.auth.LoginDeviceHook;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.user.device.repo.UserDeviceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeviceService implements LoginDeviceHook {
  public static final int DEVICE_LIMIT = 200;

  private final UserDeviceRepository devices;
  private final TokenService tokens;
  private final ObjectMapper objectMapper;
  private final long accessTtlSeconds;

  public UserDeviceService(
      UserDeviceRepository devices,
      TokenService tokens,
      ObjectMapper objectMapper,
      @Value("${bluedock.jwt.access-ttl-seconds:604800}") long accessTtlSeconds) {
    this.devices = devices;
    this.tokens = tokens;
    this.objectMapper = objectMapper;
    this.accessTtlSeconds = accessTtlSeconds;
  }

  @Override
  @Transactional
  public void onLogin(long userId, String token, String userAgent, String clientIp) {
    String hash = TokenService.hashOf(token);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("ip", clientIp == null ? "" : clientIp);
    detail.put("userAgent", userAgent == null ? "" : userAgent);
    detail.put("type", guessType(userAgent));
    String json;
    try {
      json = objectMapper.writeValueAsString(detail);
    } catch (Exception e) {
      json = "{}";
    }
    LocalDateTime expired = LocalDateTime.now().plusSeconds(Math.max(60, accessTtlSeconds));
    devices.insert(userId, hash, json, expired);
    devices.pruneOldest(userId, DEVICE_LIMIT);
  }

  public Map<String, Object> list(String token) {
    long userId = AuthContext.requireUserId();
    String currentHash = token == null || token.isBlank() ? null : TokenService.hashOf(token);
    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, Object> row : devices.listActive(userId, DEVICE_LIMIT)) {
      Map<String, Object> item = new LinkedHashMap<>(row);
      Object detailRaw = row.get("detail");
      item.put("detail", parseDetail(detailRaw == null ? null : String.valueOf(detailRaw)));
      String hash = String.valueOf(row.get("hash"));
      item.put("isCurrent", currentHash != null && currentHash.equals(hash) ? 1 : 0);
      list.add(item);
    }
    return Map.of("list", list);
  }

  @Transactional
  public void logout(Long id) {
    long userId = AuthContext.requireUserId();
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEVICE_PARAM_INVALID);
    }
    Map<String, Object> row =
        devices
            .findActive(userId, id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEVICE_NOT_FOUND));
    String hash = String.valueOf(row.get("hash"));
    devices.softDelete(id);
    tokens.revokeByHash(hash);
  }

  @Transactional
  public void edit(String token, Map<String, Object> patch) {
    long userId = AuthContext.requireUserId();
    if (token == null || token.isBlank()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEVICE_NOT_FOUND);
    }
    String hash = TokenService.hashOf(token);
    Map<String, Object> row =
        devices
            .findByHash(userId, hash)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEVICE_NOT_FOUND));
    Map<String, Object> detail = parseDetail(String.valueOf(row.get("detail")));
    boolean changed = false;
    for (String[] pair :
        new String[][] {
          {"deviceName", "device_name"},
          {"appBrand", "app_brand"},
          {"appModel", "app_model"},
          {"appOs", "app_os"}
        }) {
      Object v = patch.get(pair[0]) != null ? patch.get(pair[0]) : patch.get(pair[1]);
      if (v != null) {
        detail.put(pair[0], String.valueOf(v));
        changed = true;
      }
    }
    if (!changed) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEVICE_PARAM_INVALID);
    }
    try {
      devices.updateDetail(((Number) row.get("id")).longValue(), objectMapper.writeValueAsString(detail));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEVICE_PARAM_INVALID);
    }
  }

  public int count(long userId) {
    return devices.countActive(userId);
  }

  private Map<String, Object> parseDetail(String json) {
    if (json == null || json.isBlank() || "null".equals(json)) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private static String guessType(String userAgent) {
    if (userAgent == null) {
      return "unknown";
    }
    String u = userAgent.toLowerCase();
    if (u.contains("android")) {
      return "android";
    }
    if (u.contains("iphone") || u.contains("ipad") || u.contains("ios")) {
      return "ios";
    }
    if (u.contains("electron")
        || u.contains("bluedockdesk")
        || u.contains("mainbluedockwindow")
        || u.contains("taskdesk")
        || u.contains("maintaskwindow")) {
      return "desktop";
    }
    return "web";
  }
}
