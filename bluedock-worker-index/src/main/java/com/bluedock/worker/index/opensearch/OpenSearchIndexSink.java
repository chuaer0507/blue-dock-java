package com.bluedock.worker.index.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.search.SearchIndexEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 可选：索引事件双写 OpenSearch（失败仅打日志，不影响 MySQL docs）。 */
@Component
public class OpenSearchIndexSink {
  private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexSink.class);

  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final String url;
  private final String index;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public OpenSearchIndexSink(
      ObjectMapper objectMapper,
      @Value("${bluedock.search.opensearch.enabled:false}") boolean enabled,
      @Value("${bluedock.search.opensearch.url:http://127.0.0.1:19200}") String url,
      @Value("${bluedock.search.opensearch.index:bluedock-search}") String index) {
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.url = url == null ? "" : (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    this.index = index;
  }

  public void upsert(SearchIndexEvent event) {
    if (!enabled || event == null) {
      return;
    }
    try {
      String id = event.docType() + "-" + event.refId();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("docType", event.docType());
      body.put("refId", event.refId());
      body.put("userId", event.userId());
      body.put("projectId", event.projectId());
      body.put("title", event.title() == null ? "" : event.title());
      body.put("content", event.content() == null ? "" : event.content());
      String json = objectMapper.writeValueAsString(body);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url + "/" + index + "/_doc/" + id))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() >= 300) {
        log.warn("opensearch upsert status={} body={}", resp.statusCode(), truncate(resp.body()));
      }
    } catch (Exception e) {
      log.warn("opensearch upsert failed: {}", e.toString());
    }
  }

  public void delete(String docType, long refId) {
    if (!enabled) {
      return;
    }
    try {
      String id = docType + "-" + refId;
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url + "/" + index + "/_doc/" + id))
              .timeout(Duration.ofSeconds(5))
              .DELETE()
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() >= 300 && resp.statusCode() != 404) {
        log.warn("opensearch delete status={} body={}", resp.statusCode(), truncate(resp.body()));
      }
    } catch (Exception e) {
      log.warn("opensearch delete failed: {}", e.toString());
    }
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 200 ? s : s.substring(0, 200);
  }
}
