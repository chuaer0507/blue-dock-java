package com.bluedock.messenger.mention;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析消息正文中的用户 @ 提及（HTML / 遗留标记 / @所有人）。 */
public final class DialogMentionParser {
  private static final Pattern USER_HTML =
      Pattern.compile(
          "<span\\s+class=\"mention\\s+user\"\\s+data-id=\"(\\d+)\"[^>]*>",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ALL_HTML =
      Pattern.compile(
          "<span\\s+class=\"mention\\s+all\"[^>]*>|data-id=\"all\"",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern USER_MARK = Pattern.compile("\\[:@:(\\d+):", Pattern.CASE_INSENSITIVE);
  private static final Pattern ALL_MARK =
      Pattern.compile("\\[:@:(?:0|all):", Pattern.CASE_INSENSITIVE);

  private DialogMentionParser() {}

  public record Result(boolean all, List<Long> userIds) {}

  public static Result parse(String body) {
    if (body == null || body.isBlank()) {
      return new Result(false, List.of());
    }
    boolean all = ALL_HTML.matcher(body).find() || ALL_MARK.matcher(body).find();
    Set<Long> ids = new LinkedHashSet<>();
    Matcher html = USER_HTML.matcher(body);
    while (html.find()) {
      long id = Long.parseLong(html.group(1));
      if (id > 0) {
        ids.add(id);
      }
    }
    Matcher mark = USER_MARK.matcher(body);
    while (mark.find()) {
      long id = Long.parseLong(mark.group(1));
      if (id > 0) {
        ids.add(id);
      }
    }
    // 兼容旧 UserBotWebhook：任意 data-id 中非 all 的数字（仅当已有 user class 未命中时不重复）
    return new Result(all, List.copyOf(ids));
  }

  /** 将 mention_ids 存串解析为列表。 */
  public static List<Long> parseIdsCsv(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<Long> out = new ArrayList<>();
    for (String p : raw.split("[,\\s]+")) {
      if (p.isBlank()) {
        continue;
      }
      try {
        long id = Long.parseLong(p.trim());
        if (id > 0) {
          out.add(id);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    return out;
  }

  public static String toIdsCsv(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Long id : ids) {
      if (id == null || id <= 0) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(id);
    }
    String s = sb.toString();
    return s.length() > 1900 ? s.substring(0, 1900) : s;
  }
}
