package com.bluedock.project.web.dto;

import com.bluedock.project.domain.ProjectFlow;
import com.bluedock.project.domain.ProjectFlowItem;
import java.util.Arrays;
import java.util.List;

public record ProjectFlowView(long id, long projectId, String name, List<ProjectFlowItemView> items) {

  public static ProjectFlowView from(ProjectFlow f, List<ProjectFlowItem> items) {
    return new ProjectFlowView(
        f.getId(),
        f.getProjectId(),
        f.getName() == null ? "" : f.getName(),
        items.stream().map(ProjectFlowItemView::from).toList());
  }

  public record ProjectFlowItemView(
      long id,
      long flowId,
      String name,
      String status,
      String color,
      int sort,
      List<Long> turns,
      List<Long> userIds,
      String usertype,
      long columnId) {

    public static ProjectFlowItemView from(ProjectFlowItem it) {
      return new ProjectFlowItemView(
          it.getId(),
          it.getFlowId(),
          it.getName() == null ? "" : it.getName(),
          it.getStatus() == null ? "" : it.getStatus(),
          it.getColor() == null ? "" : it.getColor(),
          it.getSort(),
          parseIds(it.getTurns()),
          parseIds(it.getUserIds()),
          it.getUsertype() == null ? "" : it.getUsertype(),
          it.getColumnId());
    }

    private static List<Long> parseIds(String raw) {
      if (raw == null || raw.isBlank()) {
        return List.of();
      }
      return Arrays.stream(raw.split("[,|]"))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(
              s -> {
                try {
                  return Long.parseLong(s);
                } catch (NumberFormatException e) {
                  return 0L;
                }
              })
          .filter(id -> id > 0)
          .toList();
    }
  }
}
