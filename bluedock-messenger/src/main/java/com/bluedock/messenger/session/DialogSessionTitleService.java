package com.bluedock.messenger.session;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.system.ai.AiBotChatService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 对话 AI 会话标题：首条文本先写预览标题，再异步用 AI 精炼（每会话一次）。
 */
@Service
public class DialogSessionTitleService {
  private static final Logger log = LoggerFactory.getLogger(DialogSessionTitleService.class);
  private static final Duration DONE_TTL = Duration.ofDays(30);
  private static final String SYSTEM_PROMPT =
      """
      你是一个专业的标题生成器，专门为项目任务管理系统的对话内容生成精准、简洁的标题。
      要求：标题准确概括核心意图；长度 5-20 个字符；简洁；不要引号或特殊符号；直接返回标题。
      """;
  private static final ExecutorService EXEC = Executors.newVirtualThreadPerTaskExecutor();

  private final DialogRepository dialogs;
  private final StringRedisTemplate redis;
  private final ObjectProvider<AiBotChatService> aiChat;

  public DialogSessionTitleService(
      DialogRepository dialogs,
      StringRedisTemplate redis,
      ObjectProvider<AiBotChatService> aiChat) {
    this.dialogs = dialogs;
    this.redis = redis;
    this.aiChat = aiChat;
  }

  /** 用户发文本后调用；无当前会话或已生成则跳过。 */
  public void scheduleIfNeeded(long dialogId, long userId, String text) {
    if (dialogId <= 0 || userId <= 0 || text == null || text.isBlank() || "...".equals(text.trim())) {
      return;
    }
    String sessionKey = dialogs.findUserSessionKey(dialogId, userId);
    if (sessionKey == null || sessionKey.isBlank()) {
      return;
    }
    Optional<Map<String, Object>> session = dialogs.findDialogSession(dialogId, userId, sessionKey);
    if (session.isEmpty()) {
      return;
    }
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(
                RedisKeys.dialogSessionTitleDone(dialogId, userId, sessionKey), "1", DONE_TTL);
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    String preview = cut(text.trim(), 20);
    if (preview.isEmpty()) {
      preview = "Untitled";
    }
    LocalDateTime now = LocalDateTime.now();
    dialogs.updateDialogSessionTitle(dialogId, userId, sessionKey, preview, now);
    String body = text.trim();
    EXEC.execute(() -> refineWithAi(dialogId, userId, sessionKey, body));
  }

  private void refineWithAi(long dialogId, long userId, String sessionKey, String text) {
    try {
      AiBotChatService chat = aiChat.getIfAvailable();
      if (chat == null || !chat.available()) {
        return;
      }
      String title =
          chat.chat(SYSTEM_PROMPT, "请为以下内容生成一个合适的标题：\n\n" + cut(text, 2000));
      if (title == null || title.isBlank()) {
        return;
      }
      String cleaned = cut(title.trim().replace("\"", "").replace("「", "").replace("」", ""), 100);
      if (cleaned.isEmpty()) {
        return;
      }
      dialogs.updateDialogSessionTitle(dialogId, userId, sessionKey, cleaned, LocalDateTime.now());
    } catch (Exception e) {
      log.warn("session title ai refine failed: {}", e.toString());
    }
  }

  private static String cut(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.codePointCount(0, s.length()) <= max
        ? s
        : s.substring(0, s.offsetByCodePoints(0, max));
  }
}
