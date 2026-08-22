package com.bluedock.common.kafka;

public final class KafkaTopics {
  public static final String NOTIFY_SEND = "bluedock.notify.send";
  public static final String SEARCH_INDEX = "bluedock.search.index";
  public static final String REALTIME_FANOUT = "bluedock.realtime.fanout";
  public static final String FILE_PROCESS = "bluedock.file.process";
  public static final String EXPORT_RUN = "bluedock.export.run";
  /** 导出完成通知（system-msg 私聊；boot/messenger 消费） */
  public static final String EXPORT_NOTIFY = "bluedock.export.notify";
  /** 机器人 Webhook HTTP 投递 */
  public static final String USER_BOT_WEBHOOK = "bluedock.userBot.webhook";
  /** Webhook 成功后机器人文本回复（boot/messenger 消费） */
  public static final String USER_BOT_WEBHOOK_REPLY = "bluedock.userBot.webhook.reply";

  private KafkaTopics() {}
}
