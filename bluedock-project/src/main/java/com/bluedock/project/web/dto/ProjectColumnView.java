package com.bluedock.project.web.dto;

import com.bluedock.project.domain.ProjectColumn;

public record ProjectColumnView(long id, long projectId, String name, String color, int sort) {

  public static ProjectColumnView from(ProjectColumn c) {
    return new ProjectColumnView(
        c.getId(),
        c.getProjectId(),
        c.getName(),
        c.getColor() == null ? "" : c.getColor(),
        c.getSort());
  }
}
