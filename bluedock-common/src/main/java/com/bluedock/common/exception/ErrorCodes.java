package com.bluedock.common.exception;

/** 错误码分段见 docs/contract/i18n.md。 */
public final class ErrorCodes {
  public static final int BAD_REQUEST = 1000;
  public static final int UNAUTHORIZED = 1001;
  public static final int FORBIDDEN = 1002;
  public static final int NOT_FOUND = 1003;

  public static final int AUTH_FAILED = 1100;
  /** Access Token 过期 / 失效，客户端可用 refreshToken 无感续期。 */
  public static final int TOKEN_EXPIRED = -2;
  /** 需图形验证码。 */
  public static final int CAPTCHA_REQUIRED = -3;
  /** 公钥 keyId 失效 / 解密失败。 */
  public static final int PUBLIC_KEY_INVALID = -11;

  public static final int PROJECT_DENIED = 1200;
  public static final int TASK_DENIED = 1300;
  public static final int DIALOG_DENIED = 1400;
  public static final int FILE_DENIED = 1500;
  public static final int REPORT_DENIED = 1600;
  public static final int ASSISTANT_DENIED = 1700;

  private ErrorCodes() {}
}
