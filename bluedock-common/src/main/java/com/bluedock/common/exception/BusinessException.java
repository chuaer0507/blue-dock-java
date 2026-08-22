package com.bluedock.common.exception;

import com.bluedock.common.i18n.Messages;

public class BusinessException extends RuntimeException {
  private final int code;
  private final String messageKey;
  private final Object[] args;

  public BusinessException(int code, String messageKey, Object... args) {
    super(messageKey);
    this.code = code;
    this.messageKey = messageKey;
    this.args = args == null ? new Object[0] : args;
  }

  public int getCode() {
    return code;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public Object[] getArgs() {
    return args;
  }

  /** 按当前请求 Locale 解析后的文案。 */
  public String resolvedMessage() {
    return Messages.get(messageKey, args);
  }
}
