package com.bluedock.task.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskTemplate;
import com.bluedock.task.repo.TaskTemplateRepository;
import com.bluedock.task.web.dto.TaskTemplateSearchPage;
import com.bluedock.task.web.dto.TaskTemplateView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTemplateService {
  private static final int MAX_PER_PROJECT = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;

  private final TaskTemplateRepository templates;
  private final ProjectAccessService access;
  private final ProjectRepository projects;

  public TaskTemplateService(
      TaskTemplateRepository templates,
      ProjectAccessService access,
      ProjectRepository projects) {
    this.templates = templates;
    this.access = access;
    this.projects = projects;
  }

  public List<TaskTemplateView> list(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    return templates.listByProject(projectId).stream().map(TaskTemplateView::from).toList();
  }

  public List<TaskTemplateView> visible(Long currentProjectId) {
    long userId = AuthContext.requireUserId();
    List<Long> projectIds = searchProjectIds(userId, currentProjectId);
    long prefer = currentProjectId == null ? 0L : currentProjectId;
    return templates.listByProjects(projectIds, prefer).stream()
        .map(TaskTemplateView::from)
        .toList();
  }

  public TaskTemplateSearchPage search(String keyword, Long currentProjectId, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    List<Long> projectIds = searchProjectIds(userId, currentProjectId);
    int p = page == null || page < 1 ? 1 : page;
    int size =
        pageSize == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
    long total = templates.countSearch(projectIds, keyword);
    int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
    int offset = (p - 1) * size;
    List<TaskTemplateView> items =
        templates.search(projectIds, keyword, offset, size).stream()
            .map(TaskTemplateView::from)
            .toList();
    return new TaskTemplateSearchPage(
        items, new TaskTemplateSearchPage.PageMeta(p, size, total, totalPage));
  }

  /**
   * 创建任务时套用模板的副作用：可见则原子递增 use_count；不可见/不存在/目标项目关闭共享则静默忽略。
   */
  public void recordUsage(long templateId, long targetProjectId) {
    if (templateId <= 0) {
      return;
    }
    long userId = AuthContext.requireUserId();
    TaskTemplate tpl = templates.find(templateId).orElse(null);
    if (tpl == null) {
      return;
    }
    if (access.findOwner(tpl.getProjectId(), userId).isEmpty()) {
      return;
    }
    if (targetProjectId > 0
        && tpl.getProjectId() != targetProjectId
        && !projects.isTemplateShareOpen(targetProjectId)) {
      return;
    }
    templates.incrementUsage(templateId);
  }

  /**
   * 可见/搜索的项目范围：用户参与的项目；若 {@code currentProjectId} 关闭模板共享，则仅该项目。
   */
  private List<Long> searchProjectIds(long userId, Long currentProjectId) {
    List<Long> ids = projects.listProjectIdsForUser(userId);
    if (currentProjectId == null || currentProjectId <= 0) {
      return ids;
    }
    if (!projects.isTemplateShareOpen(currentProjectId)) {
      return ids.contains(currentProjectId) ? List.of(currentProjectId) : List.of();
    }
    return ids;
  }

  @Transactional
  public TaskTemplateView save(
      long projectId, Long id, String name, String title, String content) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 100) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TEMPLATE_NAME);
    }
    String ti = title == null ? "" : title.trim();
    String co = content == null ? "" : content.trim();
    if (ti.isEmpty() && co.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TEMPLATE_BODY);
    }

    LocalDateTime now = LocalDateTime.now();
    if (id != null && id > 0) {
      TaskTemplate existing =
          templates
              .find(id)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_TEMPLATE_NOT_FOUND));
      if (existing.getProjectId() != projectId) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_TEMPLATE_NOT_FOUND);
      }
      existing.setName(n);
      existing.setTitle(ti);
      existing.setContent(co);
      templates.update(existing);
      return TaskTemplateView.from(existing);
    }
    if (templates.countByProject(projectId) >= MAX_PER_PROJECT) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TEMPLATE_LIMIT, MAX_PER_PROJECT);
    }
    TaskTemplate t = new TaskTemplate();
    t.setId(IdGenerator.nextId());
    t.setProjectId(projectId);
    t.setName(n);
    t.setTitle(ti);
    t.setContent(co);
    t.setSort(templates.nextSort(projectId));
    t.setIsDefault(0);
    t.setUserId(userId);
    t.setUseCount(0);
    t.setCreatedAt(now);
    t.setUpdatedAt(now);
    templates.insert(t);
    return TaskTemplateView.from(t);
  }

  @Transactional
  public void sort(long projectId, List<?> listRaw) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    if (listRaw == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TEMPLATE_SORT);
    }
    LinkedHashSet<Long> ordered = new LinkedHashSet<>();
    for (Object o : listRaw) {
      try {
        long id = Long.parseLong(String.valueOf(o).trim());
        if (id > 0) {
          ordered.add(id);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    int index = 0;
    List<Long> handled = new ArrayList<>();
    for (Long templateId : ordered) {
      templates.updateSort(templateId, projectId, index);
      handled.add(templateId);
      index++;
    }
    for (TaskTemplate t : templates.listByProject(projectId)) {
      if (!handled.contains(t.getId())) {
        templates.updateSort(t.getId(), projectId, index++);
      }
    }
  }

  @Transactional
  public void delete(long id) {
    long userId = AuthContext.requireUserId();
    TaskTemplate t =
        templates
            .find(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_TEMPLATE_NOT_FOUND));
    access.requireManage(t.getProjectId(), userId);
    templates.delete(id);
  }

  @Transactional
  public Map<String, Object> toggleDefault(long id, long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    TaskTemplate t =
        templates
            .find(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_TEMPLATE_NOT_FOUND));
    if (t.getProjectId() != projectId) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_TEMPLATE_NOT_FOUND);
    }
    if (t.getIsDefault() == 1) {
      templates.setDefault(id, projectId, false);
      return Map.of("id", id, "isDefault", 0);
    }
    templates.clearDefault(projectId);
    templates.setDefault(id, projectId, true);
    return Map.of("id", id, "isDefault", 1);
  }
}
