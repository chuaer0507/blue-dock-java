package com.bluedock.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.model.ResultModel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer Token 鉴权。白名单路径匿名；其余 /api/** 必须有效 token。
 * 不依赖 Spring Security FilterChain，避免与后续 Security 配置冲突。
 *
 * <p>无 Bearer → {@code 1001}；Bearer 无效/过期 → {@code -2}（客户端可无感 refresh）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BearerAuthFilter extends OncePerRequestFilter {
  private static final Set<String> ANONYMOUS_EXACT =
      Set.of(
          "/api/users/login",
          "/api/users/logout",
          "/api/users/login/needCode",
          "/api/users/login/codeImage",
          "/api/users/login/codeJson",
          "/api/users/key/client",
          "/api/users/register/needInvite",
          "/api/users/email/verification",
          "/api/users/token/refresh",
          "/api/users/password/reset",
          "/api/users/register",
          "/api/users/email/code",
          "/api/project/invite/info",
          "/api/apps/badge/set",
          "/api/file/content/office",
          "/api/privacy",
          "/api/system/demo",
          "/api/system/get/updateLog");

  private final TokenService tokens;
  private final ObjectMapper objectMapper;

  public BearerAuthFilter(TokenService tokens, ObjectMapper objectMapper) {
    this.tokens = tokens;
    this.objectMapper = objectMapper;
  }

  private static final Set<String> OPTIONAL_AUTH =
      Set.of(
          "/api/system/version",
          "/api/system/prefetch",
          "/api/system/get/ip",
          "/api/system/get/chinaIp",
          "/api/system/get/info",
          "/api/users/login/qrCode",
          "/api/users/meeting/open",
          "/api/users/meeting/link",
          "/api/users/meeting/tourist");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = path(request);
    if (!path.startsWith("/api/")) {
      return true;
    }
    if (ANONYMOUS_EXACT.contains(path)) {
      return true;
    }
    // qrCode 需可选 Bearer（手机确认），走 OPTIONAL_AUTH，不可整段跳过
    if (path.startsWith("/api/users/login/") && !OPTIONAL_AUTH.contains(path)) {
      return true;
    }
    if (path.startsWith("/api/license/")) {
      return true;
    }
    if (path.startsWith("/api/ai/invoke/stream/")) {
      return true;
    }
    return path.startsWith("/api/public/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = path(request);
    try {
      String token = BearerTokens.extract(request.getHeader("Authorization"));
      if (OPTIONAL_AUTH.contains(path)) {
        if (token != null) {
          tokens.resolve(token).ifPresent(uid -> AuthContext.set(new AuthUser(uid)));
        }
        filterChain.doFilter(request, response);
        return;
      }
      if (token == null) {
        writeAuthError(response, ErrorCodes.UNAUTHORIZED, Messages.get(I18nKeys.UNAUTHORIZED));
        return;
      }
      var userId = tokens.resolve(token);
      if (userId.isEmpty()) {
        writeAuthError(
            response, ErrorCodes.TOKEN_EXPIRED, Messages.get(I18nKeys.UNAUTHORIZED_EXPIRED));
        return;
      }
      AuthContext.set(new AuthUser(userId.get()));
      filterChain.doFilter(request, response);
    } finally {
      AuthContext.clear();
    }
  }

  private static String path(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String ctx = request.getContextPath();
    if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
      return uri.substring(ctx.length());
    }
    return uri;
  }

  private void writeAuthError(HttpServletResponse response, int code, String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ResultModel.fail(code, message));
  }
}
