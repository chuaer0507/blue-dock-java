package com.bluedock.auth.security;

public final class BearerTokens {
  private BearerTokens() {}

  /** 从 Authorization 头解析 token；支持 `Bearer xxx` 或裸 token。 */
  public static String extract(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String v = authorization.trim();
    if (v.length() >= 6 && v.regionMatches(true, 0, "Bearer", 0, 6)) {
      if (v.length() == 6) {
        return null;
      }
      if (v.charAt(6) != ' ') {
        // 非标准前缀，当作裸 token
        return v;
      }
      String token = v.substring(7).trim();
      return token.isEmpty() ? null : token;
    }
    return v;
  }
}
