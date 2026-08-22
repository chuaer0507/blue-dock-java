package com.bluedock.common.deploy;

import java.security.SecureRandom;

/** 生成部署态随机凭据（超管邮箱 / 密码）。 */
public final class CredentialGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] USERNAME_ALPHABET =
      "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
  private static final char[] PASSWORD_ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
  private static final String ADMIN_EMAIL_DOMAIN = "@bluedock.local";

  private CredentialGenerator() {}

  /** 超管登录邮箱（即用户名）：`admin_<8位>@bluedock.local`。 */
  public static String adminEmail() {
    return "admin_" + randomString(USERNAME_ALPHABET, 8) + ADMIN_EMAIL_DOMAIN;
  }

  public static String randomPassword(int length) {
    return randomString(PASSWORD_ALPHABET, length);
  }

  private static String randomString(char[] alphabet, int length) {
    char[] buf = new char[length];
    for (int i = 0; i < length; i++) {
      buf[i] = alphabet[RANDOM.nextInt(alphabet.length)];
    }
    return new String(buf);
  }
}
