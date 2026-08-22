package com.bluedock.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskRepository.UserTaskRow;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.PageMeta;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserProjectPage;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskCounts;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskPage;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProjectUserTaskService {
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 100;

  private final TaskRepository tasks;
  private final ProjectRepository projects;
  private final DepartmentRepository departments;
  private final UserAccountRepository users;
  private final AdminGuard adminGuard;
  private final SystemGeneralSettingService systemSettings;
  private final ObjectMapper objectMapper;

  public ProjectUserTaskService(
      TaskRepository tasks,
      ProjectRepository projects,
      DepartmentRepository departments,
      UserAccountRepository users,
      AdminGuard adminGuard,
      SystemGeneralSettingService systemSettings,
      ObjectMapper objectMapper) {
    this.tasks = tasks;
    this.projects = projects;
    this.departments = departments;
    this.users = users;
    this.adminGuard = adminGuard;
    this.systemSettings = systemSettings;
    this.objectMapper = objectMapper;
  }

  public UserTaskCounts counts(long userId, Integer owner) {
    WorksContext ctx = resolveContext(userId);
    Integer ownerFilter = normalizeOwner(owner);
    long project = projects.countProjectsForUser(userId, ctx.projectIdsRestrict());
    long todo =
        tasks.countForUser(
            userId, ownerFilter, null, null, "uncompleted", ctx.projectIdsRestrict(), ctx.readonly());
    long done =
        tasks.countForUser(
            userId, ownerFilter, null, null, "completed", ctx.projectIdsRestrict(), ctx.readonly());
    return new UserTaskCounts(project, todo, done);
  }

  public UserTaskPage tasks(
      long userId,
      Integer owner,
      Long projectId,
      String keysJson,
      Integer page,
      Integer pageSize) {
    WorksContext ctx = resolveContext(userId);
    Integer ownerFilter = normalizeOwner(owner);
    KeysFilter keys = parseKeys(keysJson);
    int p = page == null || page < 1 ? 1 : page;
    int size =
        pageSize == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
    int offset = (p - 1) * size;

    long total =
        tasks.countForUser(
            userId,
            ownerFilter,
            projectId,
            keys.name(),
            keys.status(),
            ctx.projectIdsRestrict(),
            ctx.readonly());
    List<UserTaskRow> rows =
        tasks.listForUser(
            userId,
            ownerFilter,
            projectId,
            keys.name(),
            keys.status(),
            ctx.projectIdsRestrict(),
            ctx.readonly(),
            offset,
            size);
    LocalDate today = LocalDate.now();
    LocalDateTime dayStart = today.atStartOfDay();
    LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
    LocalDateTime now = LocalDateTime.now();
    List<UserTaskView> items =
        rows.stream().map(r -> toView(r, ctx.readonly(), dayStart, dayEnd, now)).toList();
    int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return new UserTaskPage(items, new PageMeta(p, size, total, totalPage));
  }

  /** 契约 {@code GET /api/project/user/projects}。 */
  public UserProjectPage projects(
      long userId, String archived, String keysJson, Integer page, Integer pageSize) {
    WorksContext ctx = resolveContext(userId);
    KeysFilter keys = parseKeys(keysJson);
    String arch = archived == null || archived.isBlank() ? "no" : archived.trim();
    int p = page == null || page < 1 ? 1 : page;
    int size =
        pageSize == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
    int offset = (p - 1) * size;
    long total = projects.countForUser(userId, arch, "all", keys.name(), ctx.projectIdsRestrict());
    List<com.bluedock.project.domain.Project> rows =
        projects.listForUser(
            userId, arch, "all", keys.name(), ctx.projectIdsRestrict(), offset, size);
    Boolean readonlyFlag = ctx.readonly() ? Boolean.TRUE : Boolean.FALSE;
    List<com.bluedock.project.web.dto.ProjectView> items =
        rows.stream()
            .map(row -> com.bluedock.project.web.dto.ProjectView.from(row, readonlyFlag))
            .toList();
    int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return new UserProjectPage(items, new PageMeta(p, size, total, totalPage));
  }

  private WorksContext resolveContext(long targetUserId) {
    long viewer = AuthContext.requireUserId();
    if (targetUserId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, targetUserId);
    }
    UserAccount target =
        users
            .findByUserId(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (target.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.PROJECT_USER_WORKS_DENIED);
    }
    if (viewer == targetUserId) {
      return new WorksContext(false, null);
    }
    if (adminGuard.isAdmin(viewer)) {
      return new WorksContext(false, null);
    }
    if (!systemSettings.isDepartmentOwnerProjectViewOpen()) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.PROJECT_USER_WORKS_DENIED);
    }
    if (!departments.listManagedMemberUserIds(viewer).contains(targetUserId)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.PROJECT_USER_WORKS_DENIED);
    }
    List<Long> projectIds = projects.listProjectIdsForUserOwnerView(targetUserId);
    if (projectIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.PROJECT_USER_WORKS_DENIED);
    }
    return new WorksContext(true, projectIds);
  }

  private static Integer normalizeOwner(Integer owner) {
    if (owner == null) {
      return null;
    }
    if (owner != 0 && owner != 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, owner);
    }
    return owner;
  }

  private KeysFilter parseKeys(String keysJson) {
    if (keysJson == null || keysJson.isBlank()) {
      return new KeysFilter(null, null);
    }
    try {
      Map<String, Object> keys =
          objectMapper.readValue(keysJson, new TypeReference<Map<String, Object>>() {});
      Object name = keys.get("name");
      Object status = keys.get("status");
      String n = name == null ? null : String.valueOf(name).trim();
      String s = status == null ? null : String.valueOf(status).trim().toLowerCase(Locale.ROOT);
      if (s != null && !s.isEmpty() && !"completed".equals(s) && !"uncompleted".equals(s)) {
        s = null;
      }
      return new KeysFilter(n == null || n.isEmpty() ? null : n, s == null || s.isEmpty() ? null : s);
    } catch (Exception e) {
      return new KeysFilter(null, null);
    }
  }

  private static UserTaskView toView(
      UserTaskRow row,
      boolean readonly,
      LocalDateTime dayStart,
      LocalDateTime dayEnd,
      LocalDateTime now) {
    TaskItem t = row.task();
    boolean today = false;
    boolean overdue = false;
    if (t.getCompleteAt() == null && t.getEndAt() != null) {
      LocalDateTime end = t.getEndAt();
      overdue = end.isBefore(now);
      today = !end.isBefore(dayStart) && !end.isAfter(dayEnd);
    }
    return new UserTaskView(
        t.getId(),
        t.getParentId(),
        t.getProjectId(),
        row.projectName() == null ? "" : row.projectName(),
        t.getColumnId(),
        t.getName(),
        t.getColor() == null ? "" : t.getColor(),
        t.getDescription() == null ? "" : t.getDescription(),
        t.getStartAt(),
        t.getEndAt(),
        t.getCompleteAt(),
        t.getVisibility(),
        row.owner(),
        today,
        overdue,
        readonly,
        t.getCreatedAt());
  }

  private record WorksContext(boolean readonly, List<Long> projectIdsRestrict) {}

  private record KeysFilter(String name, String status) {}
}
