package com.bluedock.project.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProjectLogDtos {
  private ProjectLogDtos() {}

  public record ProjectLogTime(String ymd, String hi, String week, String segment) {}

  public record ProjectLogTaskBrief(long id, long parentId, String name) {}

  public record ProjectLogView(
      long id,
      long projectId,
      long columnId,
      long taskId,
      long userId,
      String detail,
      Map<String, Object> record,
      ProjectLogTime time,
      String ymd,
      ProjectLogTaskBrief projectTask,
      LocalDateTime createdAt) {}

  public record ProjectLogPage(List<ProjectLogView> items, PageMeta meta) {}

  public record PageMeta(int page, int pageSize, long totalSize, int totalPage) {}
}
