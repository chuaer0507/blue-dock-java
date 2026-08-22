package com.bluedock.common.report;

/**
 * 工作报告 AI 整理草稿；由 bluedock-system {@code SystemReportAiDraftBridge} 实现（OpenAI 兼容）。
 *
 * <p>无 Bean 或 {@link #available()} 为 false 时 {@code /api/report/aiGenerate} 返回未就绪。
 */
public interface ReportAiDraftBridge {

  boolean available();

  /**
   * 基于用户已填正文整理草稿（非从零生成）。
   *
   * @param type {@code daily} / {@code weekly}
   * @param content 用户已有正文
   * @return 整理后的 Markdown 正文
   */
  String polish(String type, String content);
}
