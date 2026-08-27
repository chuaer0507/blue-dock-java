package com.bluedock.system.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.redis.RedisKeys;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Draw.io 图标搜索代理（匿名）：{@code GET /drawio/iconsearch}。 */
@RestController
public class DrawioIconSearchController {
  private static final Logger log = LoggerFactory.getLogger(DrawioIconSearchController.class);
  private static final Duration CACHE_TTL = Duration.ofDays(15);
  private static final String UPSTREAM = "https://app.diagrams.net/iconSearch";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final RestClient http = RestClient.create();

  public DrawioIconSearchController(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @GetMapping(value = "/drawio/iconsearch", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> search(
      @RequestParam(name = "q", required = false, defaultValue = "") String q,
      @RequestParam(name = "p", required = false, defaultValue = "1") String page,
      @RequestParam(name = "c", required = false, defaultValue = "25") String size) {
    String query = q == null ? "" : q.trim();
    String p = page == null || page.isBlank() ? "1" : page.trim();
    String c = size == null || size.isBlank() ? "25" : size.trim();
    String cacheKey =
        Integer.toHexString((query + "|" + p + "|" + c).hashCode());
    String cached = redis.opsForValue().get(RedisKeys.drawioIconSearch(cacheKey));
    if (cached != null && !cached.isBlank()) {
      try {
        return ResponseEntity.ok(
            objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {}));
      } catch (Exception ignored) {
        // fall through
      }
    }
    Map<String, Object> empty = new LinkedHashMap<>();
    empty.put("icons", List.of());
    empty.put("total_count", 0);
    try {
      String url =
          UPSTREAM
              + "?q="
              + URLEncoder.encode(query, StandardCharsets.UTF_8)
              + "&p="
              + URLEncoder.encode(p, StandardCharsets.UTF_8)
              + "&c="
              + URLEncoder.encode(c, StandardCharsets.UTF_8);
      String body = http.get().uri(URI.create(url)).retrieve().body(String.class);
      if (body == null || body.isBlank()) {
        return ResponseEntity.ok(empty);
      }
      Map<String, Object> parsed =
          objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
      redis.opsForValue().set(RedisKeys.drawioIconSearch(cacheKey), body, CACHE_TTL);
      return ResponseEntity.ok(parsed);
    } catch (Exception e) {
      log.warn("drawio iconsearch failed: {}", e.toString());
      return ResponseEntity.ok(empty);
    }
  }
}
