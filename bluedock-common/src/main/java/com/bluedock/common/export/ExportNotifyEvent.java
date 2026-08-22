package com.bluedock.common.export;

/** Kafka {@code bluedock.export.notify} — 导出完成/失败后由 boot/messenger 投递 system-msg 私聊。 */
public record ExportNotifyEvent(String eventId, long userId, String title, String body) {}
