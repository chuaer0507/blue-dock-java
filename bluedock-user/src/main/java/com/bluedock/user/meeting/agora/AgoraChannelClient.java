package com.bluedock.user.meeting.agora;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Agora RESTful：查询频道是否仍有人。失败时返回 false（不关房）。 */
@Component
public class AgoraChannelClient {
  private static final Logger log = LoggerFactory.getLogger(AgoraChannelClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

  public AgoraChannelClient(ObjectMapper json) {
    this.json = json;
  }

  /**
   * @return true 表示频道不存在 / 空（可关房）；false 表示有人、查询失败或未配置
   */
  public boolean isChannelEmpty(String appId, String apiKey, String apiSecret, String channel) {
    if (appId == null
        || appId.isBlank()
        || apiKey == null
        || apiKey.isBlank()
        || apiSecret == null
        || apiSecret.isBlank()
        || channel == null
        || channel.isBlank()) {
      return false;
    }
    try {
      String encodedChannel = URLEncoder.encode(channel, StandardCharsets.UTF_8);
      String url =
          "https://api.sd-rtn.com/dev/v1/channel/user/"
              + URLEncoder.encode(appId, StandardCharsets.UTF_8)
              + "/"
              + encodedChannel;
      String basic =
          Base64.getEncoder()
              .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(TIMEOUT)
              .header("Accept", "application/json")
              .header("Authorization", "Basic " + basic)
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = json.readTree(resp.body());
      if (!root.path("success").asBoolean(false)) {
        return false;
      }
      return !root.path("data").path("channel_exist").asBoolean(true);
    } catch (Exception e) {
      log.info("agora channel query failed channel={} err={}", channel, e.toString());
      return false;
    }
  }
}
