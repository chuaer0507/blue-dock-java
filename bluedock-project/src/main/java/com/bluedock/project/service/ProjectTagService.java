package com.bluedock.project.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.ProjectTag;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.repo.ProjectTagRepository;
import com.bluedock.project.web.dto.ProjectTagView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTagService {
  private static final int MAX_TAGS = 50;
  private static final int MAX_NAME = 20;

  private final ProjectTagRepository tags;
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final ProjectLogService projectLogs;

  public ProjectTagService(
      ProjectTagRepository tags,
      ProjectRepository projects,
      ProjectAccessService access,
      ProjectLogService projectLogs) {
    this.tags = tags;
    this.projects = projects;
    this.access = access;
    this.projectLogs = projectLogs;
  }

  public List<ProjectTagView> list(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    if (projects.findActive(projectId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
    }
    return tags.listByProject(projectId).stream().map(ProjectTagView::from).toList();
  }

  @Transactional
  public ProjectTagView save(long projectId, Long id, String name, String color) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    if (projects.findActive(projectId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
    }
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > MAX_NAME) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TAG_NAME_LENGTH);
    }
    if (id != null && id > 0) {
      ProjectTag existing =
          tags
              .findActive(id)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_TAG_NOT_FOUND));
      if (existing.getProjectId() != projectId) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_TAG_NOT_FOUND);
      }
      tags
          .findByProjectAndName(projectId, n)
          .filter(t -> t.getId() != id)
          .ifPresent(
              t -> {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TAG_EXISTS);
              });
      existing.setName(n);
      if (color != null) {
        existing.setColor(color.trim());
      }
      tags.update(existing);
      projectLogs.recordProject(
          projectId, 0L, "修改标签", Map.of("name", existing.getName(), "color", existing.getColor()));
      return ProjectTagView.from(existing);
    }
    if (tags.countByProject(projectId) >= MAX_TAGS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TAG_LIMIT, MAX_TAGS);
    }
    if (tags.findByProjectAndName(projectId, n).isPresent()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TAG_EXISTS);
    }
    ProjectTag t = new ProjectTag();
    t.setId(IdGenerator.nextId());
    t.setProjectId(projectId);
    t.setName(n);
    t.setColor(color == null ? "" : color.trim());
    t.setSort(tags.countByProject(projectId));
    tags.insert(t);
    projectLogs.recordProject(projectId, 0L, "添加标签", Map.of("name", t.getName(), "color", t.getColor()));
    return ProjectTagView.from(t);
  }

  @Transactional
  public void sort(long projectId, List<?> listRaw) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    if (listRaw == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TAG_SORT_INVALID);
    }
    int index = 0;
    for (Object o : listRaw) {
      long tagId = parseId(o);
      if (tagId <= 0) {
        continue;
      }
      tags.updateSort(tagId, projectId, index);
      index++;
    }
    projectLogs.recordProject(projectId, 0L, "调整标签排序", null);
  }

  @Transactional
  public void delete(long id) {
    long userId = AuthContext.requireUserId();
    ProjectTag t =
        tags
            .findActive(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_TAG_NOT_FOUND));
    access.requireManage(t.getProjectId(), userId);
    tags.softDelete(id);
    projectLogs.recordProject(
        t.getProjectId(),
        0L,
        "删除标签",
        Map.of(
            "name", t.getName() == null ? "" : t.getName(),
            "color", t.getColor() == null ? "" : t.getColor()));
  }

  /** 校验 tagIds 均属该项目；返回按项目排序后的有效 id 列表。 */
  public List<Long> filterValidTagIds(long projectId, java.util.Collection<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    for (Long id : tagIds) {
      if (id != null && id > 0) {
        ids.add(id);
      }
    }
    if (ids.isEmpty()) {
      return List.of();
    }
    return tags.listByIds(projectId, ids).stream().map(ProjectTag::getId).toList();
  }

  public List<ProjectTagView> listViewsByIds(long projectId, List<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }
    return tags.listByIds(projectId, tagIds).stream().map(ProjectTagView::from).toList();
  }

  private static long parseId(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o != null) {
      try {
        return Long.parseLong(String.valueOf(o).trim());
      } catch (NumberFormatException e) {
        return 0L;
      }
    }
    return 0L;
  }
}
