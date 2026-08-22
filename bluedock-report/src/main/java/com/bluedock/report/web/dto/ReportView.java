package com.bluedock.report.web.dto;

import com.bluedock.report.domain.Report;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ReportView(
    long id,
    String sign,
    String title,
    String type,
    long userId,
    String content,
    List<Long> receiveUserIds,
    Integer read,
    LocalDateTime receiveAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Map<String, Object> aiAnalysis) {

  public static ReportView from(Report r, List<Long> receiveUserIds, Map<String, Object> aiAnalysis) {
    return new ReportView(
        r.getId(),
        r.getSign() == null ? "" : r.getSign(),
        r.getTitle(),
        r.getType(),
        r.getUserId(),
        r.getContent() == null ? "" : r.getContent(),
        receiveUserIds == null ? List.of() : receiveUserIds,
        r.getRead(),
        r.getReceiveAt(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        aiAnalysis);
  }

  public static ReportView from(Report r, List<Long> receiveUserIds) {
    return from(r, receiveUserIds, null);
  }

  public static ReportView listItem(Report r, List<Long> receiveUserIds) {
    return new ReportView(
        r.getId(),
        r.getSign() == null ? "" : r.getSign(),
        r.getTitle(),
        r.getType(),
        r.getUserId(),
        "",
        receiveUserIds == null ? List.of() : receiveUserIds,
        r.getRead(),
        r.getReceiveAt(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        null);
  }
}
