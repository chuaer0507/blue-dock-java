package com.bluedock.system.service;

import com.bluedock.system.config.BlueDockPublicProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * 契约 {@code GET /api/system/get/updateLog}：解析 Keep a Changelog 风格的 {@code ## [version]} 分段。
 */
@Service
public class SystemUpdateLogService {
  private static final Logger log = LoggerFactory.getLogger(SystemUpdateLogService.class);
  private static final Pattern SECTION = Pattern.compile("(?m)^## \\[(.*?)]\\s*");

  private final BlueDockPublicProperties props;

  public SystemUpdateLogService(BlueDockPublicProperties props) {
    this.props = props;
  }

  public Map<String, Object> updateLog(Integer take) {
    int n = take == null ? 50 : take;
    n = Math.min(100, Math.max(10, n));
    String content = readChangelog();
    List<Map<String, String>> sections = parseSections(content, n);
    String logVersion = sections.isEmpty() ? "" : sections.getFirst().get("title");
    StringBuilder sb = new StringBuilder();
    for (Map<String, String> item : sections) {
      sb.append("## ").append(item.get("title")).append(item.get("content"));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("logVersion", logVersion);
    out.put("updateLog", sb.toString());
    return out;
  }

  String readChangelog() {
    Path path = Path.of(props.getChangelog().getPath()).toAbsolutePath().normalize();
    if (Files.isRegularFile(path)) {
      try {
        return Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException e) {
        log.warn("read changelog {}: {}", path, e.toString());
      }
    }
    try {
      ClassPathResource cp = new ClassPathResource("CHANGELOG.md");
      if (cp.exists()) {
        return StreamUtils.copyToString(cp.getInputStream(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      log.warn("read classpath CHANGELOG.md: {}", e.toString());
    }
    return "";
  }

  static List<Map<String, String>> parseSections(String content, int take) {
    List<Map<String, String>> out = new ArrayList<>();
    if (content == null || content.isBlank() || take <= 0) {
      return out;
    }
    Matcher m = SECTION.matcher(content);
    List<int[]> spans = new ArrayList<>();
    List<String> titles = new ArrayList<>();
    while (m.find()) {
      titles.add(m.group(1).trim());
      spans.add(new int[] {m.start(), m.end()});
    }
    for (int i = 0; i < titles.size() && out.size() < take; i++) {
      int bodyStart = spans.get(i)[1];
      int bodyEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : content.length();
      Map<String, String> row = new LinkedHashMap<>();
      row.put("title", titles.get(i));
      row.put("content", content.substring(bodyStart, bodyEnd));
      out.add(row);
    }
    return out;
  }
}
