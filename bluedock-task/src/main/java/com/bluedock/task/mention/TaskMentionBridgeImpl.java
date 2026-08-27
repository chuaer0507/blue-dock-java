package com.bluedock.task.mention;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.project.TaskMentionBridge;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.service.TaskRelationService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TaskMentionBridgeImpl implements TaskMentionBridge {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern HTML_MENTION =
      Pattern.compile(
          "<span\\s+class=\"mention\\s+task\"\\s+data-id=\"(\\d+)\"[^>]*>",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern LEGACY_MENTION = Pattern.compile("\\[:#:(\\d+):");

  private final TaskRepository tasks;
  private final TaskRelationService relations;

  public TaskMentionBridgeImpl(TaskRepository tasks, TaskRelationService relations) {
    this.tasks = tasks;
    this.relations = relations;
  }

  @Override
  public void recordMentionsFromMessage(long dialogId, long messageId, long userId, String msgBody) {
    String text = extractText(msgBody);
    if (text.isEmpty()) {
      return;
    }
    Set<Long> targets = parseMentionIds(text);
    if (targets.isEmpty()) {
      return;
    }
    List<Long> sources = tasks.listIdsByDialogId(dialogId);
    if (sources.isEmpty()) {
      return;
    }
    for (Long sourceTaskId : sources) {
      for (Long targetTaskId : targets) {
        try {
          relations.link(sourceTaskId, targetTaskId, dialogId, messageId, userId);
        } catch (BusinessException ignored) {
          // 任务不存在 / 非成员 / 自关联：静默跳过
        }
      }
    }
  }

  static String extractText(String msgBody) {
    if (msgBody == null || msgBody.isBlank()) {
      return "";
    }
    String raw = msgBody.trim();
    if (raw.startsWith("{")) {
      try {
        JsonNode root = JSON.readTree(raw);
        if (root.hasNonNull("text")) {
          return root.get("text").asString("");
        }
      } catch (Exception ignored) {
        // 非 JSON 则整段当文本
      }
    }
    return raw;
  }

  static Set<Long> parseMentionIds(String text) {
    Set<Long> ids = new LinkedHashSet<>();
    Matcher html = HTML_MENTION.matcher(text);
    while (html.find()) {
      long id = Long.parseLong(html.group(1));
      if (id > 0) {
        ids.add(id);
      }
    }
    Matcher legacy = LEGACY_MENTION.matcher(text);
    while (legacy.find()) {
      long id = Long.parseLong(legacy.group(1));
      if (id > 0) {
        ids.add(id);
      }
    }
    return ids;
  }
}
