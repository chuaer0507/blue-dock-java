package com.bluedock.file.office;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Minimal HS256 JWT for OnlyOffice (no extra dependency). */
public final class OfficeJwt {
  private OfficeJwt() {}

  public static String sign(String secret, String payloadJson) {
    if (secret == null || secret.isBlank()) {
      return "";
    }
    String header = b64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload = b64Url(payloadJson);
    String data = header + "." + payload;
    return data + "." + b64UrlBytes(hmac(secret, data));
  }

  private static byte[] hmac(String secret, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("hmac failed", e);
    }
  }

  private static String b64Url(String s) {
    return b64UrlBytes(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String b64UrlBytes(byte[] raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }
}
