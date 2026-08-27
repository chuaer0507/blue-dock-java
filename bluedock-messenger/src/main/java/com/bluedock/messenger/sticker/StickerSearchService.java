package com.bluedock.messenger.sticker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 在线表情搜索（搜狗图片 WAP 接口）；失败返回空列表，不影响主路径。
 * 另提供服务端拉图（发 sticker 时绕过浏览器 CORS）。
 */
@Service
public class StickerSearchService {
  private static final Logger log = LoggerFactory.getLogger(StickerSearchService.class);
  private static final String ENDPOINT = "https://pic.sogou.com/napi/wap/searchlist";
  private static final int MAX_RESULTS = 40;
  static final int MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024;

  private final HttpClient http;
  private final ObjectMapper json;

  public StickerSearchService(ObjectMapper objectMapper) {
    this.json = objectMapper == null ? new ObjectMapper() : objectMapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  }

  public List<Map<String, Object>> search(String key) {
    String keyword = key == null ? "" : key.trim();
    if (keyword.isEmpty()) {
      return List.of();
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("initQuery", keyword + " 表情");
      body.put("queryFrom", "wap");
      body.put("ie", "utf8");
      body.put("keyword", keyword + " 表情");
      body.put("showMode", 0);
      body.put("start", 1);
      body.put("reqType", "client");
      body.put("reqFrom", "wap_result");
      body.put("prevIsRedis", "n");
      body.put("pagetype", 0);
      body.put("amsParams", List.of());
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(ENDPOINT))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .header("Referer", "https://pic.sogou.com/")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        log.debug("sticker search http {}", resp.statusCode());
        return List.of();
      }
      return parseItems(resp.body());
    } catch (Exception e) {
      log.warn("sticker search failed: {}", e.toString());
      return List.of();
    }
  }

  /** 下载表情图；非法 URL / 非图片 / 过大返回 {@code null}。 */
  public StickerBytes download(String src) {
    URI uri;
    try {
      uri = validatePublicHttpUrl(src);
    } catch (IllegalArgumentException e) {
      return null;
    }
    try {
      HttpRequest req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(15))
              .header("User-Agent", "BlueDockSticker/1.0")
              .GET()
              .build();
      HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        log.debug("sticker download http {}", resp.statusCode());
        return null;
      }
      byte[] bytes = resp.body();
      if (bytes == null || bytes.length == 0 || bytes.length > MAX_DOWNLOAD_BYTES) {
        return null;
      }
      String contentType =
          resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
      String ext = extensionFromContentType(contentType);
      if (ext == null) {
        ext = extensionFromPath(uri.getPath());
      }
      if (ext == null) {
        return null;
      }
      return new StickerBytes(ext, bytes);
    } catch (Exception e) {
      log.warn("sticker download failed: {}", e.toString());
      return null;
    }
  }

  List<Map<String, Object>> parseItems(String raw) throws Exception {
    JsonNode root = json.readTree(raw == null ? "{}" : raw);
    if (root == null || root.path("status").asInt(-1) != 0) {
      return List.of();
    }
    JsonNode items = root.path("data").path("picResult").path("items");
    if (!items.isArray() || items.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (JsonNode item : items) {
      if (item == null || !item.isObject()) {
        continue;
      }
      int height = item.path("thumbHeight").asInt(0);
      int width = item.path("thumbWidth").asInt(0);
      if (height <= 10 || width <= 10) {
        continue;
      }
      String src = item.path("thumbUrl").asString("").trim();
      if (src.isEmpty()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", item.path("title").asString(""));
      row.put("src", src);
      row.put("height", height);
      row.put("width", width);
      out.add(row);
      if (out.size() >= MAX_RESULTS) {
        break;
      }
    }
    return out;
  }

  /** 仅允许公网 http(s)；拒绝私网 / 本机。 */
  public static URI validatePublicHttpUrl(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty() || value.length() > 2048) {
      throw new IllegalArgumentException("empty");
    }
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("bad uri");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("scheme");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("host");
    }
    String h = host.toLowerCase(Locale.ROOT);
    if ("localhost".equals(h) || h.endsWith(".localhost") || "metadata.google.internal".equals(h)) {
      throw new IllegalArgumentException("local");
    }
    try {
      InetAddress addr = InetAddress.getByName(host);
      if (addr.isAnyLocalAddress()
          || addr.isLoopbackAddress()
          || addr.isLinkLocalAddress()
          || addr.isSiteLocalAddress()
          || addr.isMulticastAddress()) {
        throw new IllegalArgumentException("private");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("resolve");
    }
    return uri;
  }

  static String extensionFromContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    String ct = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return switch (ct) {
      case "image/gif" -> "gif";
      case "image/png" -> "png";
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/webp" -> "webp";
      case "image/bmp" -> "bmp";
      default -> null;
    };
  }

  static String extensionFromPath(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    int dot = path.lastIndexOf('.');
    if (dot < 0 || dot == path.length() - 1) {
      return null;
    }
    String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
    return switch (ext) {
      case "gif", "png", "jpg", "jpeg", "webp", "bmp" -> ext.equals("jpeg") ? "jpg" : ext;
      default -> null;
    };
  }

  public record StickerBytes(String extension, byte[] bytes) {}
}
