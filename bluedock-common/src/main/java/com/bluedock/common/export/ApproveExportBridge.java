package com.bluedock.common.export;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 审批数据导出查询；由 approve 插件实现。
 *
 * <p>无 Bean 或 {@link #available()} 为 false 时，导出 API 拒绝受理。
 */
public interface ApproveExportBridge {

  boolean available();

  /**
   * 查询审批导出行。
   *
   * <p>约定列键：{@code id} · {@code processName} · {@code title} · {@code requesterNickname} ·
   * {@code status} · {@code createdAt} · {@code completedAt}
   *
   * @param processName 流程分类（必填）
   * @param status 状态过滤；空表示全部
   * @param start 起始日（含）
   * @param end 结束日（含）
   */
  List<Map<String, Object>> query(String processName, String status, LocalDate start, LocalDate end);
}
