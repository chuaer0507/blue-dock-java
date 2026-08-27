package com.bluedock.system.apps.service;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.config.AppsProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 可选 AppStore 生命周期 HTTP Hook（无 Docker / 无 Shell）。
 *
 * <p>配置 {@code bluedock.apps.lifecycle-hook-url} 后，对 install / update / uninstall POST JSON。
 */
@Component
public class AppLifecycleHookClient {
  private static final Logger log = LoggerFactory.getLogger(AppLifecycleHookClient.class);

  private final AppsProperties props;
  private final ObjectMapper objectMapper;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public AppLifecycleHookClient(AppsProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
  }

  /**
   * @param event {@code install} / {@code update} / {@code uninstall}
   * @return {@code true} 已配置且成功；未配置视为成功
   */
  public boolean notify(String event, String appId, String name, String version) {
    String url = props.getLifecycleHookUrl() == null ? "" : props.getLifecycleHookUrl().trim();
    if (!StringUtils.hasText(url)) {
      return true;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("event", event == null ? "" : event);
    body.put("appId", appId == null ? "" : appId);
    body.put("name", name == null ? "" : name);
    body.put("version", version == null || version.isBlank() ? "1.0.0" : version);
    body.put("at", Instant.now().toString());
    int timeoutMs = Math.max(1000, Math.min(props.getLifecycleHookTimeoutMs(), 60_000));
    try {
      String json = objectMapper.writeValueAsString(body);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        log.warn(
            "apps lifecycle hook http {} for event={} appId={}",
            resp.statusCode(),
            event,
            appId);
        return false;
      }
      return true;
    } catch (Exception e) {
      log.warn("apps lifecycle hook failed event={} appId={}: {}", event, appId, e.toString());
      return false;
    }
  }

  /** install/update：Hook 失败时按 fail-open 策略处理；严格模式先执行 rollback 再抛错。 */
  public void afterMutate(boolean ok, String event, String appId, Runnable rollback) {
    if (ok) {
      return;
    }
    if (props.isLifecycleHookFailOpen()) {
      return;
    }
    if (rollback != null) {
      try {
        rollback.run();
      } catch (Exception e) {
        log.warn("apps lifecycle rollback failed event={} appId={}: {}", event, appId, e.toString());
      }
    }
    throw new BusinessException(
        ErrorCodes.BAD_REQUEST, I18nKeys.APPS_LIFECYCLE_HOOK_FAILED, event, appId);
  }
}
