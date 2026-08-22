package com.bluedock.boot.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 轮询未发布 outbox 行并投递 Kafka（至少一次）。多实例用 {@code FOR UPDATE SKIP LOCKED}。
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(
    prefix = "bluedock.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxDispatchService dispatch;

  public OutboxPoller(OutboxDispatchService dispatch) {
    this.dispatch = dispatch;
  }

  @Scheduled(fixedDelayString = "${bluedock.outbox.poll-ms:500}")
  public void schedule() {
    try {
      dispatch.pollOnce();
    } catch (Exception e) {
      log.warn("outbox poll failed: {}", e.toString());
    }
  }
}
