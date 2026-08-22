package com.bluedock.project.web.dto;

import com.bluedock.project.domain.ProjectTag;

public record ProjectTagView(long id, long projectId, String name, String color, int sort) {

  public static ProjectTagView from(ProjectTag t) {
    return new ProjectTagView(
        t.getId(),
        t.getProjectId(),
        t.getName() == null ? "" : t.getName(),
        t.getColor() == null ? "" : t.getColor(),
        t.getSort());
  }
}
