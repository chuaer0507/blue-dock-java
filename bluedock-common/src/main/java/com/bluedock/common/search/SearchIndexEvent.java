package com.bluedock.common.search;

/** Kafka {@code bluedock.search.index} 载荷。 */
public record SearchIndexEvent(
    String eventId,
    String action,
    String docType,
    long refId,
    long userId,
    long projectId,
    String title,
    String content) {

  public static final String ACTION_UPSERT = "upsert";
  public static final String ACTION_DELETE = "delete";
  /** 全量重建：由 worker 扫源表回填；docType 可为 all 或具体类型。 */
  public static final String ACTION_REBUILD = "rebuild";

  public static final String TYPE_CONTACT = "contact";
  public static final String TYPE_PROJECT = "project";
  public static final String TYPE_TASK = "task";
  public static final String TYPE_FILE = "file";
  public static final String TYPE_MESSAGE = "message";
}
