package com.bluedock.system.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LicenseOnlineClientTest {
  HttpServer server;
  String base;
  LicenseOnlineClient client;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    client = new LicenseOnlineClient(new ObjectMapper());
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void normalizeBase_stripsSlash() {
    assertEquals("https://a.example", LicenseOnlineClient.normalizeBase("https://a.example/"));
    assertEquals("", LicenseOnlineClient.normalizeBase("  "));
  }

  @Test
  void post_ok() {
    server.createContext(
        "/v1/license/email/send",
        exchange -> {
          byte[] body = "{\"sent\":true,\"expiresIn\":600}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    Map<String, Object> out =
        client.post(base, "/v1/license/email/send", Map.of("email", "a@b.com"));
    assertEquals(true, out.get("sent"));
  }

  @Test
  void post_mapsBusinessError() {
    server.createContext(
        "/v1/license/trial",
        exchange -> {
          byte[] body =
              "{\"ok\":false,\"messageKey\":\"license.trial_used\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(400, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> client.post(base, "/v1/license/trial", Map.of("sn", "S")));
    assertEquals(I18nKeys.LICENSE_TRIAL_USED, ex.getMessageKey());
  }

  @Test
  void post_emptyBase_unavailable() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> client.post("", "/v1/x", Map.of()));
    assertEquals(I18nKeys.LICENSE_ONLINE_UNAVAILABLE, ex.getMessageKey());
  }
}
