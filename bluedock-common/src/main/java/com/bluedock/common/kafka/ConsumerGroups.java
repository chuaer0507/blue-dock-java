package com.bluedock.common.kafka;

public final class ConsumerGroups {
  public static final String NOTIFY = "bluedock-worker-notify";
  public static final String INDEX = "bluedock-worker-index";
  public static final String REALTIME = "bluedock-realtime";
  /** boot 集群内单次消费机器人 Webhook 回复 */
  public static final String USER_BOT_WEBHOOK_REPLY = "bluedock-boot-userBot-webhook-reply";
  /** 导出异步任务（由 notify worker 兼消费） */
  public static final String EXPORT = "bluedock-worker-export";
  /** boot 集群内单次消费导出 system-msg 私聊 */
  public static final String EXPORT_NOTIFY = "bluedock-boot-export-notify";

  private ConsumerGroups() {}
}
