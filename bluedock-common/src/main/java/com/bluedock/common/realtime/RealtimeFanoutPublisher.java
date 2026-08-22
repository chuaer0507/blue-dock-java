package com.bluedock.common.realtime;

/** 发布实时扇出事件（实现侧走 Kafka）。 */
public interface RealtimeFanoutPublisher {
  void publish(RealtimeFanoutEvent event);
}
