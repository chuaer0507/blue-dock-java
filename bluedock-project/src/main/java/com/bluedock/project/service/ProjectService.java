package com.bluedock.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.ProjectGroupBridge;
import com.bluedock.common.project.TaskColumnCascadeBridge;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectColumnView;
import com.bluedock.project.web.dto.ProjectView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
  private static final String[] DEFAULT_COLUMNS = {"未完成", "进行中", "已完成"};
  private static final String[] DEFAULT_COLORS = {"#909399", "#409EFF", "#67C23A"};
  private static final int MAX_COLUMNS = 30;

  private final ProjectRepository projects;
  private final ProjectColumnRepository columns;
  private final ProjectAccessService access;
  private final ProjectPermissionService projectPermissions;
  private final ProjectLogService projectLogs;
  private final SearchIndexPublisher searchIndex;
  private final ObjectMapper objectMapper;
  private final ProjectGroupBridge groupBridge;
  private final TaskColumnCascadeBridge columnCascade;
  private final ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;

  public ProjectService(
      ProjectRepository projects,
      ProjectColumnRepository columns,
      ProjectAccessService access,
      ProjectPermissionService projectPermissions,
      ProjectLogService projectLogs,
      SearchIndexPublisher searchIndex,
      ObjectMapper objectMapper,
      @Autowired(required = false) ProjectGroupBridge groupBridge,
      @Autowired(required = false) TaskColumnCascadeBridge columnCascade,
      ObjectProvider<RealtimeFanoutPublisher> realtimeFanout) {
    this.projects = projects;
    this.columns = columns;
    this.access = access;
    this.projectPermissions = projectPermissions;
    this.projectLogs = projectLogs;
    this.searchIndex = searchIndex;
    this.objectMapper = objectMapper;
    this.groupBridge = groupBridge;
    this.columnCascade = columnCascade;
    this.realtimeFanout = realtimeFanout;
  }

  public List<ProjectView> lists(boolean includeArchived) {
    return lists(includeArchived ? "all" : "no", "all", null, null);
  }

  /**
   * 项目列表筛选。
   *
   * @param archived no/yes/all；与 {@code includeArchived} 互斥时以 archived 为准
   * @param type all/team/personal
   * @param name 名称关键字；也可从 {@code keysJson} 的 name 字段读取
   */
  public List<ProjectView> lists(String archived, String type, String name, String keysJson) {
    long userId = AuthContext.requireUserId();
    String arch = normalizeArchived(archived);
    String t = normalizeType(type);
    String keyword = resolveNameKeyword(name, keysJson);
    return projects.listForUser(userId, arch, t, keyword).stream().map(ProjectView::from).toList();
  }

  private static String normalizeArchived(String archived) {
    if (archived == null || archived.isBlank()) {
      return "no";
    }
    String v = archived.trim().toLowerCase();
    if ("yes".equals(v) || "no".equals(v) || "all".equals(v)) {
      return v;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_LIST_ARCHIVED_INVALID);
  }

  private static String normalizeType(String type) {
    if (type == null || type.isBlank()) {
      return "all";
    }
    String v = type.trim().toLowerCase();
    if ("all".equals(v) || "team".equals(v) || "personal".equals(v)) {
      return v;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_LIST_TYPE_INVALID);
  }

  private String resolveNameKeyword(String name, String keysJson) {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    if (keysJson == null || keysJson.isBlank()) {
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> keys = objectMapper.readValue(keysJson, Map.class);
      Object n = keys.get("name");
      if (n == null) {
        return null;
      }
      String s = String.valueOf(n).trim();
      return s.isEmpty() ? null : s;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_LIST_KEYS_INVALID);
    }
  }

  public ProjectView one(long projectId) {
    long userId = AuthContext.requireUserId();
    int myOwner = access.requireMember(projectId, userId);
    Project p =
        projects
            .findActive(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    p.setMyOwner(myOwner);
    projects.findMemberTopAt(projectId, userId).ifPresent(p::setMyTopAt);
    return ProjectView.from(p);
  }

  @Transactional
  public ProjectView add(String name, String description, Integer isPersonal) {
    return add(name, description, isPersonal, null);
  }

  @Transactional
  public ProjectView add(String name, String description, Integer isPersonal, String columnsCsv) {
    long userId = AuthContext.requireUserId();
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 100) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_NAME_LENGTH);
    }
    int personalFlag = isPersonal != null && isPersonal == 1 ? 1 : 0;
    if (personalFlag == 1 && projects.countPersonalForUser(userId) > 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERSONAL_LIMIT);
    }

    List<String> columnNames = parseColumnNames(columnsCsv);
    if (columnNames.size() > MAX_COLUMNS) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_COLUMNS_LIMIT, MAX_COLUMNS);
    }

    LocalDateTime now = LocalDateTime.now();
    Project p = new Project();
    p.setId(IdGenerator.nextId());
    p.setName(n);
    p.setDescription(description == null ? "" : description.trim());
    p.setUserId(userId);
    p.setIsPersonal(personalFlag);
    p.setDialogId(0L);
    p.setCreatedAt(now);
    p.setUpdatedAt(now);
    p.setMyOwner(ProjectAccessService.OWNER_OWNER);

    projects.insert(p);
    projects.insertMember(
        IdGenerator.nextId(), p.getId(), userId, ProjectAccessService.OWNER_OWNER);
    for (int i = 0; i < columnNames.size(); i++) {
      ProjectColumn col = new ProjectColumn();
      col.setId(IdGenerator.nextId());
      col.setProjectId(p.getId());
      col.setName(columnNames.get(i));
      col.setColor(DEFAULT_COLORS[i % DEFAULT_COLORS.length]);
      col.setSort(i);
      columns.insert(col);
    }
    if (personalFlag == 0) {
      ensureProjectGroup(p);
    }
    projectLogs.recordProject(p.getId(), 0L, "创建项目", null);
    publishProjectIndex(SearchIndexEvent.ACTION_UPSERT, p);
    return ProjectView.from(p);
  }

  /** 解析 `columns` 逗号串；空则回落默认三列（客户端选 columnTemplate 后传入列名）。 */
  static List<String> parseColumnNames(String columnsCsv) {
    List<String> out = new ArrayList<>();
    if (columnsCsv != null && !columnsCsv.isBlank()) {
      LinkedHashSet<String> uniq = new LinkedHashSet<>();
      for (String part : columnsCsv.split(",")) {
        String t = part.trim();
        if (!t.isEmpty()) {
          if (t.length() > 50) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_COLUMN_NAME_LENGTH);
          }
          uniq.add(t);
        }
      }
      out.addAll(uniq);
    }
    if (out.isEmpty()) {
      for (String d : DEFAULT_COLUMNS) {
        out.add(d);
      }
    }
    return out;
  }

  @Transactional
  public ProjectView update(
      long projectId,
      String name,
      String description,
      String archiveMethod,
      Integer archiveDays,
      String aiAutoAnalyze,
      String taskTemplateShare,
      String departmentOwnerView) {
    long userId = AuthContext.requireUserId();
    int myOwner = access.requireManage(projectId, userId);
    Project p =
        projects
            .findActive(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    String oldName = p.getName();
    String oldDescription = p.getDescription() == null ? "" : p.getDescription();
    String oldMethod = p.getArchiveMethod() == null ? "system" : p.getArchiveMethod();
    int oldDays = p.getArchiveDays();
    String oldAi = p.getAiAutoAnalyze() == null ? "open" : p.getAiAutoAnalyze();
    String oldShare = p.getTaskTemplateShare() == null ? "open" : p.getTaskTemplateShare();
    int oldDept = p.getDepartmentOwnerView();

    boolean nameChanged = false;
    boolean descChanged = false;
    if (name != null) {
      String n = name.trim();
      if (n.isEmpty() || n.length() > 100) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_NAME_LENGTH);
      }
      nameChanged = !n.equals(oldName);
      p.setName(n);
    }
    if (description != null) {
      String d = description.trim();
      if (d.length() > 500) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_DESC_LENGTH);
      }
      descChanged = !d.equals(oldDescription);
      p.setDescription(d);
    }
    if (archiveMethod != null) {
      String m = archiveMethod.trim();
      if (!"system".equals(m) && !"custom".equals(m)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_ARCHIVE_METHOD_INVALID);
      }
      if (!m.equals(oldMethod)) {
        projectLogs.recordProject(
            projectId, 0L, "修改归档方式", Map.of("change", List.of(oldMethod, m)));
      }
      p.setArchiveMethod(m);
    }
    if ("custom".equals(p.getArchiveMethod()) && archiveDays != null) {
      if (archiveDays < 1 || archiveDays > 365) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_ARCHIVE_DAYS_INVALID);
      }
      if (archiveDays != oldDays) {
        projectLogs.recordProject(
            projectId,
            0L,
            "修改自动归档天数",
            Map.of("change", List.of(String.valueOf(oldDays), String.valueOf(archiveDays))));
      }
      p.setArchiveDays(archiveDays);
    }
    if (aiAutoAnalyze != null) {
      String v = openClose(aiAutoAnalyze);
      if (!v.equals(oldAi)) {
        projectLogs.recordProject(
            projectId, 0L, "修改AI自动分析", Map.of("change", List.of(oldAi, v)));
      }
      p.setAiAutoAnalyze(v);
    }
    if (taskTemplateShare != null) {
      String v = openClose(taskTemplateShare);
      if (!v.equals(oldShare)) {
        projectLogs.recordProject(
            projectId, 0L, "修改共享模板", Map.of("change", List.of(oldShare, v)));
      }
      p.setTaskTemplateShare(v);
    }
    if (departmentOwnerView != null) {
      String v = openClose(departmentOwnerView);
      int flag = "open".equals(v) ? 1 : 0;
      if (flag != oldDept) {
        projectLogs.recordProject(
            projectId,
            0L,
            "修改负责人视角可见",
            Map.of("change", List.of(oldDept == 0 ? "close" : "open", v)));
      }
      p.setDepartmentOwnerView(flag);
    }
    projects.update(p);
    p.setMyOwner(myOwner);
    if (p.getIsPersonal() == 0) {
      ensureProjectGroup(p);
    }
    if (nameChanged) {
      projectLogs.recordProject(
          projectId, 0L, "修改项目名称", Map.of("change", List.of(oldName + " => " + p.getName())));
    }
    if (descChanged) {
      projectLogs.recordProject(projectId, 0L, "修改项目介绍", null);
    }
    publishProjectIndex(SearchIndexEvent.ACTION_UPSERT, p);
    return ProjectView.from(p);
  }

  private static String openClose(String raw) {
    String v = raw == null ? "" : raw.trim();
    if (!"open".equals(v) && !"close".equals(v)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SETTING_OPEN_CLOSE);
    }
    return v;
  }

  /** 同步项目群成员（成员变更后调用）。 */
  public void syncProjectGroup(long projectId) {
    projects.findActive(projectId).ifPresent(this::ensureProjectGroup);
  }

  private void ensureProjectGroup(Project p) {
    if (groupBridge == null || p.getIsPersonal() == 1) {
      return;
    }
    long dialogId =
        groupBridge.ensureGroup(
            p.getId(),
            p.getName(),
            projects.findOwnerUserId(p.getId()).orElse(p.getUserId()),
            projects.listMemberUserIds(p.getId()));
    if (p.getDialogId() != dialogId) {
      projects.updateDialogId(p.getId(), dialogId);
      p.setDialogId(dialogId);
    }
  }

  private void publishProjectIndex(String action, Project p) {
    SearchIndexEvent event =
        new SearchIndexEvent(
            UUID.randomUUID().toString().replace("-", ""),
            action,
            SearchIndexEvent.TYPE_PROJECT,
            p.getId(),
            p.getUserId(),
            p.getId(),
            p.getName(),
            p.getDescription());
    searchIndex.publish(event);
  }

  public List<ProjectColumnView> columnLists(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    if (projects.findActive(projectId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
    }
    return columns.listByProject(projectId).stream().map(ProjectColumnView::from).toList();
  }

  /** 列详情。契约：{@code GET /api/project/column/one}。 */
  public ProjectColumnView columnOne(long columnId) {
    long userId = AuthContext.requireUserId();
    ProjectColumn col =
        columns
            .findActive(columnId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_COLUMN_NOT_FOUND));
    access.requireMember(col.getProjectId(), userId);
    return ProjectColumnView.from(col);
  }

  @Transactional
  public ProjectColumnView columnAdd(long projectId, String name, String color) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    projectPermissions.require(projectId, userId, ProjectPermissionCodes.TASK_LIST_ADD, null);
    if (projects.findActive(projectId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
    }
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 50) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_COLUMN_NAME_LENGTH);
    }
    int sort = columns.listByProject(projectId).size();
    ProjectColumn col = new ProjectColumn();
    col.setId(IdGenerator.nextId());
    col.setProjectId(projectId);
    col.setName(n);
    col.setColor(color == null ? "" : color.trim());
    col.setSort(sort);
    columns.insert(col);
    projectLogs.recordProject(projectId, col.getId(), "创建列表：" + col.getName(), null);
    ProjectColumnView view = ProjectColumnView.from(col);
    publishColumnFanout(RealtimeEventTypes.COLUMN_CREATED, projectId, view);
    return view;
  }

  @Transactional
  public ProjectColumnView columnUpdate(long columnId, String name, String color, Integer sort) {
    long userId = AuthContext.requireUserId();
    ProjectColumn col =
        columns
            .findActive(columnId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_COLUMN_NOT_FOUND));
    access.requireMember(col.getProjectId(), userId);
    projectPermissions.require(
        col.getProjectId(), userId, ProjectPermissionCodes.TASK_LIST_UPDATE, null);
    String oldName = col.getName();
    String oldColor = col.getColor() == null ? "" : col.getColor();
    if (name != null) {
      String n = name.trim();
      if (n.isEmpty() || n.length() > 50) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_COLUMN_NAME_LENGTH);
      }
      if (!n.equals(oldName)) {
        projectLogs.recordProject(
            col.getProjectId(), col.getId(), "修改列表名称：" + oldName + " => " + n, null);
      }
      col.setName(n);
    }
    if (color != null) {
      String c = color.trim();
      if (!c.equals(oldColor)) {
        projectLogs.recordProject(
            col.getProjectId(), col.getId(), "修改列表颜色：" + oldColor + " => " + c, null);
      }
      col.setColor(c);
    }
    if (sort != null) {
      col.setSort(sort);
    }
    columns.update(col);
    ProjectColumnView view = ProjectColumnView.from(col);
    publishColumnFanout(RealtimeEventTypes.COLUMN_UPDATED, col.getProjectId(), view);
    return view;
  }

  /**
   * 软删列；级联软删列内任务（含子任务）。至少保留一列。契约：{@code GET /api/project/column/remove}。
   */
  @Transactional
  public void columnRemove(long columnId) {
    long userId = AuthContext.requireUserId();
    ProjectColumn col =
        columns
            .findActive(columnId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_COLUMN_NOT_FOUND));
    access.requireMember(col.getProjectId(), userId);
    projectPermissions.require(
        col.getProjectId(), userId, ProjectPermissionCodes.TASK_LIST_REMOVE, null);
    if (columns.countActiveByProject(col.getProjectId()) <= 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_COLUMN_LAST);
    }
    if (columnCascade != null) {
      columnCascade.softDeleteByColumn(col.getProjectId(), columnId, userId);
    }
    columns.softDelete(columnId);
    projectLogs.recordProject(col.getProjectId(), columnId, "删除列表：" + col.getName(), null);
    publishColumnFanout(
        RealtimeEventTypes.COLUMN_DELETED,
        col.getProjectId(),
        new ProjectColumnView(
            col.getId(),
            col.getProjectId(),
            col.getName(),
            col.getColor() == null ? "" : col.getColor(),
            col.getSort()));
  }

  private void publishColumnFanout(String type, long projectId, ProjectColumnView column) {
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
    data.put("columnId", column.id());
    if (RealtimeEventTypes.COLUMN_DELETED.equals(type)) {
      data.put("deleted", true);
      data.put("name", column.name() == null ? "" : column.name());
    } else {
      data.put("column", column);
    }
    RealtimeFanoutEvent event =
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""), type, List.copyOf(userIds), data);
    publisher.publish(event);
  }

  /** 当前用户项目列表拖拽排序；仅更新本人的 {@code project_users.sort}。 */
  @Transactional
  public void userSort(List<?> listRaw) {
    long userId = AuthContext.requireUserId();
    if (listRaw == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_SORT_INVALID);
    }
    int index = 0;
    for (Object o : listRaw) {
      long projectId = parseId(o);
      if (projectId <= 0) {
        continue;
      }
      projects.updateMemberSort(userId, projectId, index);
      index++;
    }
  }

  /** 切换当前用户对项目的置顶。 */
  @Transactional
  public Map<String, Object> top(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    var toggled =
        projects
            .toggleMemberTop(userId, projectId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", projectId);
    out.put("topAt", toggled.topAt());
    return out;
  }

  private static long parseId(Object o) {
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
