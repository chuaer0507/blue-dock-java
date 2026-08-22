package com.bluedock.common.ai;

/** OpenAI 兼容 chat 调用失败。 */
public class OpenAiChatException extends RuntimeException {
  public OpenAiChatException(String message) {
    super(message);
  }

  public OpenAiChatException(String message, Throwable cause) {
    super(message, cause);
  }
}
