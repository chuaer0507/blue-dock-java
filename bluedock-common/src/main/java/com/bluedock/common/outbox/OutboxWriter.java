package com.bluedock.common.outbox;

/**
 * 事务内写入 {@code bluedock_outbox}；由 boot 侧 poller 投递 Kafka。
 *
 * <p>有活跃事务且本 Bean 存在时，Kafka 发布器应入队而非直发。
 */
public interface OutboxWriter {

  /**
   * @param topic Kafka topic（须用 {@link com.bluedock.common.kafka.KafkaTopics}）
   * @param messageKey 分区键，可空
   * @param payload JSON 字符串
   */
  void enqueue(String topic, String messageKey, String payload);
}
