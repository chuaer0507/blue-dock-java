package com.bluedock.user.bot;

import java.util.LinkedHashMap;
import java.util.Map;

/** 系统机器人邮箱前缀 → 显示名。 */
public final class SystemUserBots {
  private static final Map<String, String> NAMES = new LinkedHashMap<>();

  static {
    NAMES.put("system-msg", "系统消息");
    NAMES.put("task-alert", "任务提醒");
    NAMES.put("todo-alert", "待办提醒");
    NAMES.put("attendance", "签到打卡");
    NAMES.put("anon-msg", "匿名消息");
    NAMES.put("approval-alert", "审批");
    NAMES.put("meeting-alert", "会议通知");
    NAMES.put("okr-alert", "OKR 提醒");
    NAMES.put("bot-manager", "机器人管理");
    NAMES.put("ai-openai", "ChatGPT");
    NAMES.put("ai-claude", "Claude");
    NAMES.put("ai-deepseek", "DeepSeek");
  }

  private SystemUserBots() {}

  public static Map<String, String> all() {
    return Map.copyOf(NAMES);
  }

  public static String nameOfEmail(String emailOrPrefix) {
    if (emailOrPrefix == null || emailOrPrefix.isBlank()) {
      return "";
    }
    String prefix = emailOrPrefix;
    int at = emailOrPrefix.indexOf('@');
    if (at > 0) {
      prefix = emailOrPrefix.substring(0, at);
    }
    return NAMES.getOrDefault(prefix, "");
  }

  public static boolean isSystemEmail(String email) {
    return email != null
        && email.endsWith("@bot.system")
        && !nameOfEmail(email).isEmpty();
  }

  public static String emailOf(String prefix) {
    return prefix + "@bot.system";
  }
}
