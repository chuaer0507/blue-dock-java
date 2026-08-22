package com.bluedock.system.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 隐私政策 HTML（匿名）。 */
@RestController
public class PrivacyController {

  @GetMapping(value = "/api/privacy", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> privacy() throws IOException {
    ClassPathResource resource = new ClassPathResource("static/privacy.html");
    if (!resource.exists()) {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
          .body(fallbackHtml());
    }
    try (InputStream in = resource.getInputStream()) {
      String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
          .body(html);
    }
  }

  private static String fallbackHtml() {
    return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
          <title>隐私政策</title>
          <style>
            body{font-family:system-ui,-apple-system,sans-serif;max-width:720px;margin:2rem auto;padding:0 1rem;line-height:1.6;color:#222}
            h1{font-size:1.5rem}
          </style>
        </head>
        <body>
          <h1>隐私政策</h1>
          <p>BlueDock 尊重并保护用户个人信息。我们仅在提供协作服务所必需的范围内处理账号、任务与协作数据，不会向无关第三方出售个人信息。</p>
          <p>你可以在个人设置中管理资料、设备会话，并按产品流程申请注销账号。注销后主资料将清除，历史协作内容可能保留为匿名记录。</p>
          <p>如需定制本页，请替换服务端资源 <code>static/privacy.html</code>。</p>
        </body>
        </html>
        """;
  }
}
