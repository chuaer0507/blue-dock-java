package com.bluedock.system.ai;

import com.bluedock.common.report.ReportAiDraftBridge;
import org.springframework.stereotype.Component;

/** 工作报告 AI 整理：走 {@link AiBotChatService}（OpenAI 兼容）。 */
@Component
public class SystemReportAiDraftBridge implements ReportAiDraftBridge {
  private final AiBotChatService chat;

  public SystemReportAiDraftBridge(AiBotChatService chat) {
    this.chat = chat;
  }

  @Override
  public boolean available() {
    return chat.available();
  }

  @Override
  public String polish(String type, String content) {
    String kind = "weekly".equalsIgnoreCase(type) ? "周报" : "日报";
    String system =
        "你是企业协作系统的工作汇报助手。请把用户提供的"
            + kind
            + "草稿整理为结构清晰的 Markdown 正文："
            + "保留原有事实与数据，不要编造未提及的内容；可用小标题与列表；只输出整理后的正文，不要前言或解释。";
    String user = "请整理以下" + kind + "草稿：\n\n" + (content == null ? "" : content.trim());
    return chat.chat(system, user);
  }
}
