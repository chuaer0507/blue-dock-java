package com.bluedock.common.realtime;

/** WebSocket / fanout 事件名（camelCase type 字段）。 */
public final class RealtimeEventTypes {
  public static final String DIALOG_MESSAGE = "dialog.message";
  public static final String DIALOG_MESSAGE_UPDATE = "dialog.message.update";
  public static final String DIALOG_MESSAGE_WITHDRAW = "dialog.message.withdraw";
  public static final String DIALOG_MESSAGE_EMOJI = "dialog.message.emoji";
  public static final String DIALOG_MESSAGE_TOP = "dialog.message.top";
  public static final String DIALOG_MESSAGE_TODO = "dialog.message.todo";
  public static final String DIALOG_MESSAGE_STREAM = "dialog.message.stream";
  public static final String OPERATION = "operation";
  public static final String OPERATION_RESULT = "operationResult";
  public static final String APP_BADGE = "appBadge";
  public static final String TASK_CREATED = "task.created";
  public static final String TASK_UPDATED = "task.updated";
  public static final String TASK_DELETED = "task.deleted";
  public static final String COLUMN_CREATED = "column.created";
  public static final String COLUMN_UPDATED = "column.updated";
  public static final String COLUMN_DELETED = "column.deleted";
  public static final String PROJECT_SORT = "project.sort";
  public static final String PRESENCE_ONLINE = "presence.online";
  public static final String PRESENCE_OFFLINE = "presence.offline";
  public static final String PING = "ping";
  public static final String PONG = "pong";

  private RealtimeEventTypes() {}
}
