package com.bluedock.common.notify.apppush;

import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 推送 HTTP 客户端（customizedcast / alias；上游 msgapi.umeng.com）。
 *
 * <p>签名：MD5(POST + url + body + masterSecret)。
 */
public final class AppPushClient {
  public static final String SEND_URL = "https://msgapi.umeng.com/api/send";

  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ObjectMapper objectMapper;

  public AppPushClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AppPushSendResult sendCustomizedCast(
      String appKey,
      String masterSecret,
      String aliasType,
      List<String> aliases,
      String title,
      String text,
      Map<String, Object> extra,
      Integer badge,
      boolean production,
      boolean ios)
      throws Exception {
    if (appKey == null
        || appKey.isBlank()
        || masterSecret == null
        || masterSecret.isBlank()
        || aliases == null
        || aliases.isEmpty()) {
      throw new IllegalArgumentException("appPush params incomplete");
    }
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("appkey", appKey);
    root.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
    root.put("type", "customizedcast");
    root.put("alias_type", aliasType == null || aliasType.isBlank() ? "bluedock" : aliasType);
    root.put("alias", String.join(",", aliases));
    root.put("production_mode", production ? "true" : "false");
    root.put("description", "bluedock-notify");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", title == null ? "" : title);
    body.put("text", text == null ? "" : text);
    body.put("after_open", "go_app");
    if (ios) {
      body.put("ticker", title == null ? "" : title);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("display_type", "notification");
    payload.put("body", body);
    if (extra != null && !extra.isEmpty()) {
      payload.put("extra", extra);
    }
    if (ios && badge != null) {
      Map<String, Object> aps = new LinkedHashMap<>();
      aps.put("badge", Math.min(99, Math.max(0, badge)));
      payload.put("aps", aps);
    }
    root.put("payload", payload);
    if (!ios && badge != null) {
      root.put("set_badge", Math.min(99, Math.max(0, badge)));
    }

    String postBody = objectMapper.writeValueAsString(root);
    String sign = md5("POST" + SEND_URL + postBody + masterSecret);
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(SEND_URL + "?sign=" + sign))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(postBody, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> resp =
        http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return new AppPushSendResult(postBody, resp.body());
  }

  private static String md5(String s) throws Exception {
    byte[] dig = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(dig);
  }
}
