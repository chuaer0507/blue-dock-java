package com.bluedock.common.realtime;

import java.util.List;
import java.util.Map;

/**
 * Kafka {@code bluedock.realtime.fanout} 载荷；各实例消费后仅推送本机 WebSocket 连接。
 */
public record RealtimeFanoutEvent(
    String eventId, String type, List<Long> userIds, Map<String, Object> data) {}
