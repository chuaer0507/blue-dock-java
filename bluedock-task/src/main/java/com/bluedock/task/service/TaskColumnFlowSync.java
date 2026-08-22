package com.bluedock.task.service;

import com.bluedock.project.domain.ProjectFlowItem;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 列 ↔ 工作流节点联动：节点配置 {@code columnId} 时，拖列同步 {@code flowItemId}/{@code
 * flowItemName}；绑定 {@code end} 时补写完成时间（不回清空）。
 */
@Component
public class TaskColumnFlowSync {
  private final ProjectFlowRepository flowItems;
  private final TaskRepository tasks;

  public TaskColumnFlowSync(ProjectFlowRepository flowItems, TaskRepository tasks) {
    this.flowItems = flowItems;
    this.tasks = tasks;
  }

  /** 看板 sort：任务换列后按列绑定节点同步（含子任务）。 */
  public void syncAfterColumnMove(long taskId, long projectId, long columnId) {
    ProjectFlowItem item = boundItem(projectId, columnId);
    if (item == null) {
      return;
    }
    boolean end = isEnd(item);
    String name = nameOf(item);
    tasks.applyBoundFlowFromColumn(taskId, projectId, item.getId(), name, end);
    tasks.applyBoundFlowFromColumnForChildren(taskId, projectId, item.getId(), name, end);
  }

  /** 仅子任务同步（主任务已在实体层处理，避免覆盖 {@code completed} 显式参数）。 */
  public void syncChildrenAfterColumnMove(long parentId, long projectId, long columnId) {
    ProjectFlowItem item = boundItem(projectId, columnId);
    if (item == null) {
      return;
    }
    tasks.applyBoundFlowFromColumnForChildren(
        parentId, projectId, item.getId(), nameOf(item), isEnd(item));
  }

  /**
   * 实体级同步（{@code update}/{@code move}）：仅当目标列有绑定节点时改写 flow；非 end 不清除
   * {@code completeAt}。
   */
  public void applyBoundFlowToEntity(TaskItem t) {
    if (t == null || t.getColumnId() <= 0) {
      return;
    }
    ProjectFlowItem item = boundItem(t.getProjectId(), t.getColumnId());
    if (item == null) {
      return;
    }
    t.setFlowItemId(item.getId());
    t.setFlowItemName(nameOf(item));
    if (isEnd(item) && t.getCompleteAt() == null) {
      t.setCompleteAt(LocalDateTime.now());
    }
  }

  private ProjectFlowItem boundItem(long projectId, long columnId) {
    return flowItems.findActiveItemByColumn(projectId, columnId).orElse(null);
  }

  private static boolean isEnd(ProjectFlowItem item) {
    String status = item.getStatus() == null ? "" : item.getStatus().trim().toLowerCase(Locale.ROOT);
    return "end".equals(status);
  }

  private static String nameOf(ProjectFlowItem item) {
    return item.getName() == null ? "" : item.getName();
  }
}
