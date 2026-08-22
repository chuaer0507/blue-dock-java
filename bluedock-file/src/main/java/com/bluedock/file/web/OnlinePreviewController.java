package com.bluedock.file.web;

import com.bluedock.file.config.UploadProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地上传文件在线预览（匿名）：{@code GET /online/preview?key=}。
 *
 * <p>{@code key} 形如 {@code /uploads/...?name=x&ext=pdf}，路径须落在上传根目录内。
 */
@RestController
public class OnlinePreviewController {
  private static final long PDF_INLINE_MAX = 10L * 1024 * 1024;

  private final UploadProperties upload;

  public OnlinePreviewController(UploadProperties upload) {
    this.upload = upload;
  }

  @GetMapping("/online/preview")
  public ResponseEntity<?> preview(@RequestParam("key") String key) throws Exception {
    if (key == null || key.isBlank()) {
      return ResponseEntity.notFound().build();
    }
    URI uri = URI.create(key.contains("://") ? key : "file://" + key);
    String pathPart = uri.getPath() == null ? "" : uri.getPath();
    Map<String, String> query = parseQuery(uri.getRawQuery());
    String name = query.getOrDefault("name", Path.of(pathPart).getFileName().toString());
    String ext = query.getOrDefault("ext", "").toLowerCase(Locale.ROOT);

    Path root = Path.of(upload.getBaseDir()).toAbsolutePath().normalize();
    String relative = pathPart;
    if (relative.startsWith("/uploads/")) {
      relative = relative.substring("/uploads/".length());
    } else if (relative.startsWith("uploads/")) {
      relative = relative.substring("uploads/".length());
    } else if (relative.startsWith("/")) {
      relative = relative.substring(1);
    }
    Path file = root.resolve(relative).normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
      return ResponseEntity.notFound().build();
    }
    long size = Files.size(file);
    if ("pdf".equals(ext) || name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
      if (size > PDF_INLINE_MAX) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(downloadHtml(name, size, "/uploads/" + relative.replace('\\', '/')));
      }
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitize(name) + "\"")
          .contentType(MediaType.APPLICATION_PDF)
          .body(new FileSystemResource(file));
    }
    String ctype = Files.probeContentType(file);
    MediaType mt =
        ctype == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(ctype);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitize(name) + "\"")
        .contentType(mt)
        .body(new FileSystemResource(file));
  }

  private static Map<String, String> parseQuery(String raw) {
    Map<String, String> out = new java.util.LinkedHashMap<>();
    if (raw == null || raw.isBlank()) {
      return out;
    }
    for (String part : raw.split("&")) {
      int i = part.indexOf('=');
      if (i <= 0) {
        continue;
      }
      String k = URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8);
      String v = URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
      out.put(k, v);
    }
    return out;
  }

  private static String sanitize(String name) {
    return name.replace("\"", "").replace("\r", "").replace("\n", "");
  }

  private static String downloadHtml(String name, long size, String url) {
    String readable =
        size >= 1024 * 1024
            ? String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0))
            : String.format(Locale.ROOT, "%.0f KB", size / 1024.0);
    return """
        <!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"/>
        <title>%s</title></head><body style="font-family:system-ui;padding:2rem">
        <h1>%s</h1><p>文件过大（%s），请下载后查看。</p>
        <p><a href="%s">点击下载</a></p></body></html>
        """
        .formatted(escape(name), escape(name), readable, escape(url));
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
  }
}
