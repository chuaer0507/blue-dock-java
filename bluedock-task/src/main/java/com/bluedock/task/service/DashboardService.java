package com.bluedock.task.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.DashboardTeamStatsView;
import com.bluedock.task.web.dto.TaskView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final ProjectRepository projects;
  private final TaskRepository tasks;
  private final DepartmentRepository departments;

  public DashboardService(
      ProjectRepository projects, TaskRepository tasks, DepartmentRepository departments) {
    this.projects = projects;
    this.tasks = tasks;
    this.departments = departments;
  }

  public DashboardTeamStatsView teamStats(Long departmentId) {
    Scope scope = resolveScope(departmentId);
    List<TaskItem> all = tasks.listTeamTasks(scope.projectIds(), scope.members());

    LocalDate today = LocalDate.now();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime soonEnd = today.plusDays(3).atTime(LocalTime.MAX);
    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDateTime weekStartAt = weekStart.atStartOfDay();

    int uncompleted = 0;
    int overdue = 0;
    int soon = 0;
    int weekCompleted = 0;
    Map<String, Integer> priority = new LinkedHashMap<>();
    priority.put("unset", 0);
    priority.put("1", 0);
    priority.put("2", 0);
    priority.put("3", 0);
    priority.put("4", 0);

    for (TaskItem t : all) {
      boolean done = t.getCompleteAt() != null;
      if (done) {
        if (!t.getCompleteAt().isBefore(weekStartAt)) {
          weekCompleted++;
        }
        continue;
      }
      uncompleted++;
      bumpPriority(priority, t.getPriorityLevel());
      if (t.getEndAt() != null && t.getEndAt().isBefore(now)) {
        overdue++;
      } else if (t.getEndAt() != null && !t.getEndAt().isAfter(soonEnd)) {
        soon++;
      }
    }

    return new DashboardTeamStatsView(
        uncompleted, overdue, soon, weekCompleted, scope.members(), scope.projectIds(), priority);
  }

  public List<TaskView> teamTasks(
      Long departmentId, String type, Long memberId, Integer level, Integer page, Integer pageSize) {
    Scope scope = resolveScope(departmentId);
    List<Long> projectIds = scope.projectIds();
    List<Long> members = scope.members();
    List<TaskItem> all = tasks.listTeamTasks(projectIds, members);

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime soonEnd = LocalDate.now().plusDays(3).atTime(LocalTime.MAX);

    List<TaskItem> filtered;
    if (memberId != null && memberId > 0) {
      if (!members.contains(memberId)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DASHBOARD_TYPE_INVALID);
      }
      filtered =
          all.stream()
              .filter(t -> t.getCompleteAt() == null)
              .filter(t -> ownerIs(t, memberId))
              .collect(Collectors.toCollection(ArrayList::new));
    } else if (level != null) {
      filtered =
          all.stream()
              .filter(t -> t.getCompleteAt() == null)
              .filter(t -> level == -1 ? t.getPriorityLevel() <= 0 : t.getPriorityLevel() == level)
              .collect(Collectors.toCollection(ArrayList::new));
    } else {
      String ty = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
      filtered =
          switch (ty) {
            case "uncompleted" -> all.stream().filter(t -> t.getCompleteAt() == null).toList();
            case "overdue" ->
                all.stream()
                    .filter(t -> t.getCompleteAt() == null)
                    .filter(t -> t.getEndAt() != null && t.getEndAt().isBefore(now))
                    .toList();
            case "soon" ->
                all.stream()
                    .filter(t -> t.getCompleteAt() == null)
                    .filter(
                        t ->
                            t.getEndAt() != null
                                && !t.getEndAt().isBefore(now)
                                && !t.getEndAt().isAfter(soonEnd))
                    .toList();
            case "hi" ->
                all.stream()
                    .filter(t -> t.getCompleteAt() == null)
                    .filter(t -> t.getPriorityLevel() >= 3)
                    .toList();
            case "noowner" ->
                all.stream()
                    .filter(t -> t.getCompleteAt() == null)
                    .filter(t -> !hasOwner(t))
                    .toList();
            default ->
                throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DASHBOARD_TYPE_INVALID);
          };
    }

    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(50, Math.max(1, pageSize));
    int from = Math.min((p - 1) * size, filtered.size());
    int to = Math.min(from + size, filtered.size());
    return filtered.subList(from, to).stream().map(TaskView::from).toList();
  }

  private Scope resolveScope(Long departmentId) {
    long userId = AuthContext.requireUserId();
    if (departmentId != null && departmentId > 0) {
      if (!departments.canManage(userId, departmentId)) {
        throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.DASHBOARD_DEPT_DENIED);
      }
      List<Long> members = departments.listActiveMemberIdsInTree(departmentId);
      List<Long> projectIds = projects.listProjectIdsForDepartmentOwnerView(members);
      return new Scope(projectIds, members);
    }
    // 兼容：未传部门时回退项目 owner≥1
    List<Long> projectIds = projects.listManagedProjectIds(userId);
    List<Long> members = projects.listDistinctMemberUserIds(projectIds);
    return new Scope(projectIds, members);
  }

  private boolean ownerIs(TaskItem t, long memberId) {
    return t.getOwnerUserId() == memberId;
  }

  private boolean hasOwner(TaskItem t) {
    return t.getOwnerUserId() > 0;
  }

  private static void bumpPriority(Map<String, Integer> priority, int level) {
    if (level <= 0) {
      priority.merge("unset", 1, Integer::sum);
    } else if (level >= 4) {
      priority.merge("4", 1, Integer::sum);
    } else {
      priority.merge(String.valueOf(level), 1, Integer::sum);
    }
  }

  private record Scope(List<Long> projectIds, List<Long> members) {}
}
