package com.bluedock.task.web.dto;

import com.bluedock.task.domain.TaskItem;
import java.time.LocalDateTime;
import java.util.List;

public record TaskView(
    long id,
    long parentId,
    long projectId,
    long columnId,
    String name,
    String color,
    String description,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime completeAt,
    int visibility,
    List<Long> visibilityUserIds,
    int priorityLevel,
    String priorityName,
    String priorityColor,
    long flowItemId,
    String flowItemName,
    List<Long> tagIds,
    List<Long> ownerUserIds,
    List<Long> assistUserIds,
    int sort,
    int loop,
    LocalDateTime loopAt,
    long userId,
    LocalDateTime createdAt) {

  public static TaskView from(TaskItem t) {
    return from(t, List.of(), List.of(), List.of(), List.of());
  }

  public static TaskView from(TaskItem t, List<Long> visibilityUserIds) {
    return from(t, visibilityUserIds, List.of(), List.of(), List.of());
  }

  public static TaskView from(TaskItem t, List<Long> visibilityUserIds, List<Long> tagIds) {
    return from(t, visibilityUserIds, tagIds, List.of(), List.of());
  }

  public static TaskView from(
      TaskItem t,
      List<Long> visibilityUserIds,
      List<Long> tagIds,
      List<Long> ownerUserIds,
      List<Long> assistUserIds) {
    return new TaskView(
        t.getId(),
        t.getParentId(),
        t.getProjectId(),
        t.getColumnId(),
        t.getName(),
        t.getColor() == null ? "" : t.getColor(),
        t.getDescription() == null ? "" : t.getDescription(),
        t.getStartAt(),
        t.getEndAt(),
        t.getCompleteAt(),
        t.getVisibility(),
        visibilityUserIds == null ? List.of() : List.copyOf(visibilityUserIds),
        t.getPriorityLevel(),
        t.getPriorityName() == null ? "" : t.getPriorityName(),
        t.getPriorityColor() == null ? "" : t.getPriorityColor(),
        t.getFlowItemId(),
        t.getFlowItemName() == null ? "" : t.getFlowItemName(),
        tagIds == null ? List.of() : List.copyOf(tagIds),
        ownerUserIds == null ? List.of() : List.copyOf(ownerUserIds),
        assistUserIds == null ? List.of() : List.copyOf(assistUserIds),
        t.getSort(),
        t.getLoop(),
        t.getLoopAt(),
        t.getUserId(),
        t.getCreatedAt());
  }
}
