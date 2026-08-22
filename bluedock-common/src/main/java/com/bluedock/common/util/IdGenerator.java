package com.bluedock.common.util;

/**
 * 业务主键入口（Snowflake BIGINT）。禁止各模块自行拼 ID。
 * workerId / datacenterId 由配置注入前可用默认单机值。
 */
public final class IdGenerator {
  private static final long EPOCH = 1704067200000L; // 2024-01-01 UTC
  private static final long WORKER_ID_BITS = 5L;
  private static final long DATACENTER_ID_BITS = 5L;
  private static final long SEQUENCE_BITS = 12L;

  private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
  private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
  private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

  private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
  private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
  private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

  private static long workerId = 1L;
  private static long datacenterId = 1L;
  private static long sequence = 0L;
  private static long lastTimestamp = -1L;

  private IdGenerator() {}

  public static synchronized void configure(long worker, long datacenter) {
    if (worker > MAX_WORKER_ID || worker < 0) {
      throw new IllegalArgumentException("workerId out of range");
    }
    if (datacenter > MAX_DATACENTER_ID || datacenter < 0) {
      throw new IllegalArgumentException("datacenterId out of range");
    }
    workerId = worker;
    datacenterId = datacenter;
  }

  public static synchronized long nextId() {
    long timestamp = System.currentTimeMillis();
    if (timestamp < lastTimestamp) {
      throw new IllegalStateException("Clock moved backwards");
    }
    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        timestamp = waitNextMillis(lastTimestamp);
      }
    } else {
      sequence = 0L;
    }
    lastTimestamp = timestamp;
    return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
        | (datacenterId << DATACENTER_ID_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence;
  }

  private static long waitNextMillis(long last) {
    long ts = System.currentTimeMillis();
    while (ts <= last) {
      ts = System.currentTimeMillis();
    }
    return ts;
  }
}
