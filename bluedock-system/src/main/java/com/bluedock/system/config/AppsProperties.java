package com.bluedock.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用市场 / 微应用运行时配置。 */
@ConfigurationProperties(prefix = "bluedock.apps")
public class AppsProperties {
  /**
   * 装/更/卸后的 HTTP 生命周期 Hook 完整 URL；空则跳过。
   *
   * <p>POST JSON：{@code {event,appId,name,version,at}}；不含 secret。
   */
  private String lifecycleHookUrl = "";
  /** Hook 请求超时（毫秒）。 */
  private int lifecycleHookTimeoutMs = 8000;
  /**
   * {@code true}：Hook 失败仅打日志，不回滚注册表（默认）。{@code false}：install/update 失败则抛错并尽力回滚。
   */
  private boolean lifecycleHookFailOpen = true;

  public String getLifecycleHookUrl() {
    return lifecycleHookUrl;
  }

  public void setLifecycleHookUrl(String lifecycleHookUrl) {
    this.lifecycleHookUrl = lifecycleHookUrl;
  }

  public int getLifecycleHookTimeoutMs() {
    return lifecycleHookTimeoutMs;
  }

  public void setLifecycleHookTimeoutMs(int lifecycleHookTimeoutMs) {
    this.lifecycleHookTimeoutMs = lifecycleHookTimeoutMs;
  }

  public boolean isLifecycleHookFailOpen() {
    return lifecycleHookFailOpen;
  }

  public void setLifecycleHookFailOpen(boolean lifecycleHookFailOpen) {
    this.lifecycleHookFailOpen = lifecycleHookFailOpen;
  }
}
