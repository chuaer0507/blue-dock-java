package com.bluedock.user.apppush.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.user.apppush.repo.AppPushAliasRepository;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppPushAliasService {
  private final AppPushAliasRepository aliases;

  public AppPushAliasService(AppPushAliasRepository aliases) {
    this.aliases = aliases;
  }

  @Transactional
  public Map<String, Object> handle(Map<String, Object> body, String token, String platformHeader) {
    if (body == null) {
      body = Map.of();
    }
    if (truthy(body.get("isDebug"))) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APP_PUSH_DEBUG);
    }
    String alias = str(body.get("alias")).trim();
    String action = str(body.get("action")).trim();
    if ("remove".equalsIgnoreCase(action)) {
      if (!alias.isEmpty()) {
        aliases.deleteByAlias(alias);
      }
      return Map.of("removed", true);
    }
    if (alias.length() < 2 || alias.length() > 64) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APP_PUSH_ALIAS_INVALID);
    }
    String platform = normalizePlatform(platformHeader, str(body.get("platform")));
    if (!"ios".equals(platform) && !"android".equals(platform)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APP_PUSH_PLATFORM_INVALID);
    }
    long userId = AuthContext.requireUserId();
    String versionName = str(body.get("appVersionName"));
    String versionCode = str(body.get("appVersion"));
    String version =
        versionCode.isEmpty()
            ? versionName
            : (versionName.isEmpty() ? versionCode : versionName + " (" + versionCode + ")");
    boolean notified = truthy(body.get("isNotified"));
    String deviceHash = token == null || token.isBlank() ? "" : TokenService.hashOf(token);
    aliases.upsert(
        userId,
        alias,
        platform,
        str(body.get("userAgent")),
        str(body.get("deviceModel")),
        deviceHash,
        version,
        notified);
    return Map.of("alias", alias, "platform", platform);
  }

  private static String normalizePlatform(String header, String body) {
    String p = body == null || body.isBlank() ? header : body;
    return p == null ? "" : p.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v == null) {
      return false;
    }
    String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    return "true".equals(s) || "1".equals(s);
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}
