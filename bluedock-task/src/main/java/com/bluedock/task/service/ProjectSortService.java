package com.bluedock.task.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板排序：{@code api/project/sort}。
 *
 * <ul>
 *   <li>{@code onlyColumn=1}：仅重排列
 *   <li>否则：按列内任务 id 列表重排，并可换列（仅未完成任务）
 * </ul>
 */
@Service
public class ProjectSortService {
  private final ProjectAccessService access;
  private final ProjectPermissionService projectPermissions;
  private final ProjectColumnRepository columns;
  private final TaskRepository tasks;
  private final ProjectLogService projectLogs;
  private final TaskColumnFlowSync columnFlowSync;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;

  public ProjectSortService(
      ProjectAccessService access,
      ProjectPermissionService projectPermissions,
      ProjectColumnRepository columns,
      TaskRepository tasks,
      ProjectLogService projectLogs,
      TaskColumnFlowSync columnFlowSync,
      ObjectMapper objectMapper,
      ObjectProvider<RealtimeFanoutPublisher> realtimeFanout) {
    this.access = access;
    this.projectPermissions = projectPermissions;
    this.columns = columns;
    this.tasks = tasks;
    this.projectLogs = projectLogs;
    this.columnFlowSync = columnFlowSync;
    this.objectMapper = objectMapper;
    this.realtimeFanout = realtimeFanout;
  }

  @Transactional
  public void sort(long projectId, boolean onlyColumn, Object sortRaw) {
    if (projectId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
    }
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    List<Map<String, Object>> items = parseSort(sortRaw);
    if (onlyColumn) {
      projectPermissions.require(
          projectId, userId, ProjectPermissionCodes.TASK_LIST_SORT, null);
      sortColumns(projectId, items);
      projectLogs.recordProject(projectId, 0L, "调整列表排序", null);
    } else {
      projectPermissions.require(projectId, userId, ProjectPermissionCodes.TASK_MOVE, null);
      sortTasks(projectId, items);
      projectLogs.recordProject(projectId, 0L, "调整任务排序", null);
    }
    publishSortFanout(projectId, onlyColumn, items);
  }

  private void publishSortFanout(
      long projectId, boolean onlyColumn, List<Map<String, Object>> items) {
    RealtimeFanoutPublisher publisher = realtimeFanout.getIfAvailable();
    if (publisher == null) {
      return;
    }
    List<Long> userIds = access.listMemberUserIds(projectId);
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("projectId", projectId);
    data.put("onlyColumn", onlyColumn);
    data.put("sort", items);
    RealtimeFanoutEvent event =
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""),
            RealtimeEventTypes.PROJECT_SORT,
            List.copyOf(userIds),
            data);
    publisher.publish(event);
  }

  private void sortColumns(long projectId, List<Map<String, Object>> items) {
    int index = 0;
    for (Map<String, Object> item : items) {
      long columnId = longVal(item.get("id"));
      if (columnId <= 0) {
        continue;
      }
      columns.updateSort(columnId, projectId, index);
      index++;
    }
  }

  private void sortTasks(long projectId, List<Map<String, Object>> items) {
    for (Map<String, Object> item : items) {
      long columnId = longVal(item.get("id"));
      if (columnId <= 0) {
        continue;
      }
      ProjectColumn col =
          columns
              .findActive(columnId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_COLUMN_NOT_FOUND));
      if (col.getProjectId() != projectId) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_COLUMN_NOT_FOUND);
      }
      Object taskRaw = item.get("task");
      if (!(taskRaw instanceof List<?> taskIds)) {
        continue;
      }
      int index = 0;
      for (Object tidObj : taskIds) {
        long taskId = longVal(tidObj);
        if (taskId <= 0) {
          continue;
        }
        TaskItem existing = tasks.findActive(taskId).orElse(null);
        if (existing != null
            && existing.getProjectId() == projectId
            && existing.getColumnId() != columnId) {
          // 换列：成员即可（细粒度 TASK_MOVE 权限矩阵落地前对齐现有 move）
          access.requireMember(projectId, AuthContext.requireUserId());
        }
        int updated = tasks.updateColumnAndSortIfIncomplete(taskId, projectId, columnId, index);
        if (updated > 0) {
          tasks.moveChildrenLocation(taskId, projectId, columnId);
          columnFlowSync.syncAfterColumnMove(taskId, projectId, columnId);
        }
        index++;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parseSort(Object sortRaw) {
    if (sortRaw == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
    }
    try {
      if (sortRaw instanceof String s) {
        if (s.isBlank()) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
        }
        return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
      }
      if (sortRaw instanceof List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            out.add((Map<String, Object>) m);
          }
        }
        return out;
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
  }

  private static long longVal(Object o) {
    if (o == null) {
      return 0L;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(o).trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
