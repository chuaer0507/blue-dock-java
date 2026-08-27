package com.bluedock.project.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.repo.ProjectPermissionRepository;
import com.bluedock.project.repo.ProjectRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectPermissionService {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProjectPermissionRepository permissions;
  private final ProjectRepository projects;
  private final ProjectAccessService access;

  /** 可选：判断 userId 是否任务负责人 / 协助人；(taskId, userId) → owner(1)/assist(0)/empty。 */
  private BiPredicate<Long, Long> taskLeaderProbe = (taskId, userId) -> false;

  private BiPredicate<Long, Long> taskAssistProbe = (taskId, userId) -> false;

  public ProjectPermissionService(
      ProjectPermissionRepository permissions,
      ProjectRepository projects,
      ProjectAccessService access) {
    this.permissions = permissions;
    this.projects = projects;
    this.access = access;
  }

  /** 由 bluedock-task 启动时注册任务角色探针，避免循环依赖。 */
  public void registerTaskRoleProbes(
      BiPredicate<Long, Long> leaderProbe, BiPredicate<Long, Long> assistProbe) {
    if (leaderProbe != null) {
      this.taskLeaderProbe = leaderProbe;
    }
    if (assistProbe != null) {
      this.taskAssistProbe = assistProbe;
    }
  }

  public Map<String, Object> get(long projectId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    Project p = requireProject(projectId);
    Map<String, List<String>> matrix = loadMatrix(projectId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("projectId", projectId);
    out.put("isPersonal", p.getIsPersonal());
    out.put("permissions", matrix);
    out.put("points", ProjectPermissionCodes.ALL_POINTS);
    return out;
  }

  @Transactional
  public Map<String, Object> update(long projectId, Object permissionsRaw) {
    long userId = AuthContext.requireUserId();
    access.requireManage(projectId, userId);
    Project p = requireProject(projectId);
    if (p.getIsPersonal() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_PERSONAL);
    }
    Map<String, List<String>> matrix = normalize(permissionsRaw);
    String json;
    try {
      json = JSON.writeValueAsString(matrix);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_INVALID);
    }
    permissions.upsert(IdGenerator.nextId(), projectId, json);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("projectId", projectId);
    out.put("permissions", matrix);
    return out;
  }

  /** 拥有者/管理员始终通过；否则按矩阵角色并集判定。 */
  public void require(
      long projectId, long userId, String point, Long taskId) {
    if (!allows(projectId, userId, point, taskId)) {
      throw new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_PERMISSION_DENIED);
    }
  }

  public boolean allows(long projectId, long userId, String point, Long taskId) {
    if (!ProjectPermissionCodes.ALL_POINT_SET.contains(point)) {
      return false;
    }
    Integer owner = access.findOwner(projectId, userId).orElse(null);
    if (owner == null) {
      return false;
    }
    if (owner == ProjectAccessService.OWNER_OWNER || owner == ProjectAccessService.OWNER_ADMIN) {
      return true;
    }
    Map<String, List<String>> matrix = loadMatrix(projectId);
    Set<String> roles = new LinkedHashSet<>();
    roles.add(ProjectPermissionCodes.ROLE_PROJECT_MEMBER);
    if (taskId != null && taskId > 0) {
      if (taskLeaderProbe.test(taskId, userId)) {
        roles.add(ProjectPermissionCodes.ROLE_TASK_LEADER);
      }
      if (taskAssistProbe.test(taskId, userId)) {
        roles.add(ProjectPermissionCodes.ROLE_TASK_ASSIST);
      }
    }
    for (String role : roles) {
      List<String> granted = matrix.getOrDefault(role, List.of());
      if (granted.contains(point)) {
        return true;
      }
    }
    return false;
  }

  public Map<String, List<String>> defaults() {
    Map<String, List<String>> m = new LinkedHashMap<>();
    m.put(ProjectPermissionCodes.ROLE_PROJECT_MEMBER, new ArrayList<>(ProjectPermissionCodes.DEFAULT_MEMBER));
    m.put(ProjectPermissionCodes.ROLE_TASK_LEADER, new ArrayList<>(ProjectPermissionCodes.ALL_POINTS));
    m.put(ProjectPermissionCodes.ROLE_TASK_ASSIST, new ArrayList<>(ProjectPermissionCodes.ALL_POINTS));
    return m;
  }

  private Map<String, List<String>> loadMatrix(long projectId) {
    return permissions
        .findJson(projectId)
        .map(this::parseStored)
        .orElseGet(this::defaults);
  }

  private Map<String, List<String>> parseStored(String json) {
    try {
      Map<String, Object> raw = JSON.readValue(json, new TypeReference<>() {});
      return normalize(raw);
    } catch (Exception e) {
      return defaults();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, List<String>> normalize(Object raw) {
    if (raw == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_INVALID);
    }
    Map<String, Object> src;
    if (raw instanceof String s) {
      if (s.isBlank()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_INVALID);
      }
      try {
        src = JSON.readValue(s, new TypeReference<>() {});
      } catch (Exception e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_INVALID);
      }
    } else if (raw instanceof Map<?, ?> m) {
      src = (Map<String, Object>) m;
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERMISSION_INVALID);
    }
    Map<String, List<String>> out = defaults();
    for (String role :
        List.of(
            ProjectPermissionCodes.ROLE_PROJECT_MEMBER,
            ProjectPermissionCodes.ROLE_TASK_LEADER,
            ProjectPermissionCodes.ROLE_TASK_ASSIST)) {
      Object v = src.get(role);
      if (v == null) {
        continue;
      }
      out.put(role, sanitizePoints(v));
    }
    return out;
  }

  private static List<String> sanitizePoints(Object v) {
    LinkedHashSet<String> kept = new LinkedHashSet<>();
    if (v instanceof Collection<?> c) {
      for (Object o : c) {
        String p = String.valueOf(o).trim();
        if (ProjectPermissionCodes.ALL_POINT_SET.contains(p)) {
          kept.add(p);
        }
      }
    } else if (v instanceof String s) {
      for (String part : s.split("[,|]")) {
        String p = part.trim();
        if (ProjectPermissionCodes.ALL_POINT_SET.contains(p)) {
          kept.add(p);
        }
      }
    }
    return new ArrayList<>(kept);
  }

  private Project requireProject(long projectId) {
    return projects
        .findActive(projectId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
  }
}
