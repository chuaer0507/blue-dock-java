package com.bluedock.boot.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.outbox")
public class OutboxProperties {

  /** 是否启用 poller；关闭后发布器在无事务时仍可直发 Kafka。 */
  private boolean enabled = true;

  /** 轮询间隔（毫秒）。 */
  private long pollMs = 500;

  /** 每批最多条数。 */
  private int batchSize = 50;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getPollMs() {
    return pollMs;
  }

  public void setPollMs(long pollMs) {
    this.pollMs = pollMs;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }
}
