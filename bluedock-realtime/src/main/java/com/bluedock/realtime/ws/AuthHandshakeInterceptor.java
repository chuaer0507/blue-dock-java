package com.bluedock.realtime.ws;

import com.bluedock.auth.service.TokenService;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {
  public static final String ATTR_USER_ID = "userId";
  /** 客户端形态：desktop / web / ios / android 等（query `client` 或 `platform`）。 */
  public static final String ATTR_CLIENT = "client";

  private final TokenService tokens;

  public AuthHandshakeInterceptor(TokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String token = extractToken(request);
    return tokens
        .resolve(token)
        .map(
            uid -> {
              attributes.put(ATTR_USER_ID, uid);
              String client = extractClient(request);
              if (client != null) {
                attributes.put(ATTR_CLIENT, client);
              }
              return true;
            })
        .orElse(false);
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  private static String extractToken(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servlet) {
      String q = servlet.getServletRequest().getParameter("token");
      if (q != null && !q.isBlank()) {
        return q.trim();
      }
    }
    String auth = request.getHeaders().getFirst("Authorization");
    if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return auth.substring(7).trim();
    }
    return null;
  }

  private static String extractClient(ServerHttpRequest request) {
    if (!(request instanceof ServletServerHttpRequest servlet)) {
      return null;
    }
    String client = servlet.getServletRequest().getParameter("client");
    if (client == null || client.isBlank()) {
      client = servlet.getServletRequest().getParameter("platform");
    }
    if (client == null || client.isBlank()) {
      return null;
    }
    return client.trim().toLowerCase(Locale.ROOT);
  }
}
