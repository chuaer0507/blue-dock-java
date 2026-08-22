package com.bluedock.common.auth;

/** 鉴权模块发信桥：由 bluedock-system 注入 SMTP 配置。 */
public interface AuthMailBridge {
  /**
   * 同步发信。
   *
   * @return {@code true} 已发出；{@code false} SMTP 未配置（调用方可回传 {@code devCode}）
   */
  boolean send(String to, String subject, String body);
}
