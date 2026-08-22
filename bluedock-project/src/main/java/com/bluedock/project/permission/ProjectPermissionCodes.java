package com.bluedock.project.permission;

import java.util.List;
import java.util.Set;

/** 项目任务权限点（与 docs/modules/project/permissions.md 对齐）。 */
public final class ProjectPermissionCodes {
  public static final String TASK_LIST_ADD = "TASK_LIST_ADD";
  public static final String TASK_LIST_UPDATE = "TASK_LIST_UPDATE";
  public static final String TASK_LIST_REMOVE = "TASK_LIST_REMOVE";
  public static final String TASK_LIST_SORT = "TASK_LIST_SORT";
  public static final String TASK_ADD = "TASK_ADD";
  public static final String TASK_UPDATE = "TASK_UPDATE";
  public static final String TASK_TIME = "TASK_TIME";
  public static final String TASK_STATUS = "TASK_STATUS";
  public static final String TASK_REMOVE = "TASK_REMOVE";
  public static final String TASK_ARCHIVED = "TASK_ARCHIVED";
  public static final String TASK_MOVE = "TASK_MOVE";

  public static final String ROLE_PROJECT_MEMBER = "project_member";
  public static final String ROLE_TASK_LEADER = "task_leader";
  public static final String ROLE_TASK_ASSIST = "task_assist";

  public static final List<String> ALL_POINTS =
      List.of(
          TASK_LIST_ADD,
          TASK_LIST_UPDATE,
          TASK_LIST_REMOVE,
          TASK_LIST_SORT,
          TASK_ADD,
          TASK_UPDATE,
          TASK_TIME,
          TASK_STATUS,
          TASK_REMOVE,
          TASK_ARCHIVED,
          TASK_MOVE);

  public static final Set<String> ALL_POINT_SET = Set.copyOf(ALL_POINTS);

  public static final List<String> DEFAULT_MEMBER =
      List.of(
          TASK_ADD,
          TASK_UPDATE,
          TASK_STATUS,
          TASK_TIME,
          TASK_ARCHIVED,
          TASK_MOVE,
          TASK_LIST_SORT);

  private ProjectPermissionCodes() {}
}
