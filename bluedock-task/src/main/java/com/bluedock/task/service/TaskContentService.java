package com.bluedock.task.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskContent;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskContentRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.TaskContentDtos.PageMeta;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentHistoryItem;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentHistoryPage;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskContentService {
  private static final int MAX_CONTENT_CHARS = 500_000;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final TaskRepository tasks;
  private final TaskContentRepository contents;
  private final ProjectAccessService access;

  public TaskContentService(
      TaskRepository tasks, TaskContentRepository contents, ProjectAccessService access) {
    this.tasks = tasks;
    this.contents = contents;
    this.access = access;
  }

  /** 最新或指定历史；无内容时返回空 Map。 */
  public Object get(long taskId, Long historyId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = requireReadable(taskId, userId);
    if (historyId != null && historyId > 0) {
      TaskContent c =
          contents
              .findByIdAndTask(historyId, taskId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCodes.NOT_FOUND, I18nKeys.TASK_CONTENT_HISTORY_NOT_FOUND));
      return TaskContentView.from(c, t.getName());
    }
    return contents
        .findLatest(taskId)
        .<Object>map(c -> TaskContentView.from(c, t.getName()))
        .orElseGet(Map::of);
  }

  public TaskContentHistoryPage history(long taskId, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    requireReadable(taskId, userId);
    int p = page == null || page < 1 ? 1 : page;
    int size =
        pageSize == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
    long total = contents.countByTask(taskId);
    int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
    List<TaskContentHistoryItem> items =
        contents.listHistory(taskId, (p - 1) * size, size).stream()
            .map(TaskContentHistoryItem::from)
            .toList();
    return new TaskContentHistoryPage(items, new PageMeta(p, size, total, totalPage));
  }

  /**
   * 追加一版富文本详情；返回短摘要写入 {@code bluedock_tasks.description}。
   * 子任务不支持。
   */
  @Transactional
  public String save(TaskItem task, String html, long operatorUserId) {
    if (task.getParentId() > 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_CONTENT_SUBTASK_FORBIDDEN);
    }
    String body = html == null ? "" : html;
    if (body.length() > MAX_CONTENT_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_CONTENT_TOO_LONG);
    }
    String summary = generateDescription(body);
    LocalDateTime now = LocalDateTime.now();
    TaskContent row = new TaskContent();
    row.setId(IdGenerator.nextId());
    row.setProjectId(task.getProjectId());
    row.setTaskId(task.getId());
    row.setUserId(operatorUserId);
    row.setDescription(summary);
    row.setContent(body);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    contents.insert(row);
    return summary;
  }

  static String generateDescription(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String text =
        html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("(?i)</p>", " ")
            .replaceAll("(?i)</li>", " ")
            .replaceAll("<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&amp;", "&")
            .replaceAll("\\s+", " ")
            .trim();
    if (text.length() <= 100) {
      return text;
    }
    return text.substring(0, 100);
  }

  private TaskItem requireReadable(long taskId, long userId) {
    TaskItem t =
        tasks
            .findActive(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    return t;
  }
}
