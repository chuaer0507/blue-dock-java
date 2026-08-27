package com.bluedock.system.license;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 官方 License 商店 HTTP 客户端（{@code bluedock.system.license-online-mode=remote}）。
 *
 * <p>约定路径相对 {@code license-online-url}，见 {@code docs/infra/license.md}。
 */
@Component
public class LicenseOnlineClient {
  private static final Logger log = LoggerFactory.getLogger(LicenseOnlineClient.class);
  private static final Duration CONNECT = Duration.ofSeconds(10);
  private static final Duration REQUEST = Duration.ofSeconds(20);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final Set<String> KNOWN_KEYS =
      Set.of(
          I18nKeys.LICENSE_DENIED,
          I18nKeys.LICENSE_INVALID,
          I18nKeys.LICENSE_SN_MISMATCH,
          I18nKeys.LICENSE_MAC_MISMATCH,
          I18nKeys.LICENSE_PEOPLE_EXCEEDED,
          I18nKeys.LICENSE_EXPIRED,
          I18nKeys.LICENSE_EMAIL_INVALID,
          I18nKeys.LICENSE_CODE_INVALID,
          I18nKeys.LICENSE_PENDING_INVALID,
          I18nKeys.LICENSE_TRIAL_USED,
          I18nKeys.LICENSE_ONLINE_UNAVAILABLE,
          I18nKeys.LICENSE_ONLINE_NOT_LOGGED);

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(CONNECT).followRedirects(HttpClient.Redirect.NEVER).build();
  private final ObjectMapper objectMapper;

  public LicenseOnlineClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> post(String baseUrl, String relativePath, Map<String, Object> body) {
    String root = normalizeBase(baseUrl);
    if (root.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_UNAVAILABLE);
    }
    String path = relativePath == null ? "" : relativePath.trim();
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    URI uri = URI.create(root + path);
    try {
      String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
      HttpRequest req =
          HttpRequest.newBuilder(uri)
              .timeout(REQUEST)
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      Map<String, Object> parsed = parseBody(resp.body());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw businessError(parsed);
      }
      if (isExplicitFailure(parsed)) {
        throw businessError(parsed);
      }
      return parsed;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("license online remote call failed: {} {}", uri, e.toString());
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_UNAVAILABLE);
    }
  }

  private Map<String, Object> parseBody(String body) throws Exception {
    if (body == null || body.isBlank()) {
      return new LinkedHashMap<>();
    }
    return objectMapper.readValue(body, MAP);
  }

  private static boolean isExplicitFailure(Map<String, Object> parsed) {
    if (parsed == null || parsed.isEmpty()) {
      return false;
    }
    Object ok = parsed.get("ok");
    if (Boolean.FALSE.equals(ok) || "false".equalsIgnoreCase(String.valueOf(ok))) {
      return true;
    }
    Object success = parsed.get("success");
    return Boolean.FALSE.equals(success) || "false".equalsIgnoreCase(String.valueOf(success));
  }

  private static BusinessException businessError(Map<String, Object> parsed) {
    String key = extractErrorKey(parsed);
    if (key != null && KNOWN_KEYS.contains(key)) {
      return new BusinessException(ErrorCodes.BAD_REQUEST, key);
    }
    return new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_UNAVAILABLE);
  }

  private static String extractErrorKey(Map<String, Object> parsed) {
    if (parsed == null) {
      return null;
    }
    for (String field : new String[] {"messageKey", "error", "code"}) {
      Object v = parsed.get(field);
      if (v == null) {
        continue;
      }
      String s = String.valueOf(v).trim();
      if (s.startsWith("license.")) {
        return s;
      }
    }
    return null;
  }

  static String normalizeBase(String baseUrl) {
    if (baseUrl == null) {
      return "";
    }
    String s = baseUrl.trim();
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }
}
