package com.bluedock.project.service;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.ProjectGroupBridge;
import com.bluedock.common.project.TaskProjectArchiveBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectInvite;
import com.bluedock.project.repo.ProjectInviteRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectInviteView;
import com.bluedock.project.web.dto.ProjectMemberChangeView;
import com.bluedock.project.web.dto.ProjectView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {
  private static final int MAX_BATCH = 100;
  private static final int INVITE_DAYS = 30;

  private final ProjectRepository projects;
  private final ProjectInviteRepository invites;
  private final ProjectAccessService access;
  private final UserAccountRepository users;
  private final ProjectService projectService;
  private final ProjectLogService projectLogs;
  private final ProjectGroupBridge groupBridge;
  private final TaskProjectArchiveBridge archiveBridge;

  public ProjectMemberService(
      ProjectRepository projects,
      ProjectInviteRepository invites,
      ProjectAccessService access,
      UserAccountRepository users,
      ProjectService projectService,
      ProjectLogService projectLogs,
      @Autowired(required = false) ProjectGroupBridge groupBridge,
      @Autowired(required = false) TaskProjectArchiveBridge archiveBridge) {
    this.projects = projects;
    this.invites = invites;
    this.access = access;
    this.users = users;
    this.projectService = projectService;
    this.projectLogs = projectLogs;
    this.groupBridge = groupBridge;
    this.archiveBridge = archiveBridge;
  }

  @Transactional
  public ProjectMemberChangeView updateMembers(long projectId, String userIds, String removeUserIds) {
    long me = AuthContext.requireUserId();
    int myRole = access.requireManage(projectId, me);
    Project p = requireTeamProject(projectId);

    List<Long> added = parseIds(userIds);
    List<Long> removed = parseIds(removeUserIds);
    if (added.isEmpty() && removed.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_MEMBER_REQUIRED);
    }
    if (added.size() + removed.size() > MAX_BATCH) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_MEMBER_BATCH, MAX_BATCH);
    }

    for (Long userId : added) {
      if (!users.existsByUserId(userId)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, userId);
      }
      if (access.findOwner(projectId, userId).isEmpty()) {
        projects.insertMember(
            IdGenerator.nextId(), projectId, userId, ProjectAccessService.OWNER_MEMBER);
      }
    }

    for (Long userId : removed) {
      if (userId == me) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_MEMBER_USE_EXIT);
      }
      int role =
          access
              .findOwner(projectId, userId)
              .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_MEMBER_NOT_FOUND, userId));
      if (role == ProjectAccessService.OWNER_OWNER) {
        throw new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_CANNOT_REMOVE_OWNER);
      }
      if (role == ProjectAccessService.OWNER_ADMIN && myRole != ProjectAccessService.OWNER_OWNER) {
        throw new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_ONLY_OWNER_REMOVE_ADMIN);
      }
      projects.deleteMember(projectId, userId);
    }

    projectService.syncProjectGroup(projectId);
    projectLogs.recordProject(projectId, 0L, "修改项目成员", null);
    return new ProjectMemberChangeView(p.getId(), projects.listMemberUserIds(projectId));
  }

  @Transactional
  public ProjectInviteView invite(long projectId) {
    long me = AuthContext.requireUserId();
    access.requireManage(projectId, me);
    requireTeamProject(projectId);

    return invites
        .findActiveByProject(projectId)
        .map(i -> new ProjectInviteView(i.getCode(), i.getProjectId(), i.getExpiredAt()))
        .orElseGet(
            () -> {
              LocalDateTime now = LocalDateTime.now();
              ProjectInvite invite = new ProjectInvite();
              invite.setId(IdGenerator.nextId());
              invite.setProjectId(projectId);
              invite.setCode(UUID.randomUUID().toString().replace("-", ""));
              invite.setUserId(me);
              invite.setExpiredAt(now.plusDays(INVITE_DAYS));
              invite.setCreatedAt(now);
              invites.insert(invite);
              return new ProjectInviteView(invite.getCode(), projectId, invite.getExpiredAt());
            });
  }

  public ProjectView inviteInfo(String code) {
    ProjectInvite invite = requireValidInvite(code);
    Project p =
        projects
            .findActive(invite.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    return ProjectView.from(p);
  }

  @Transactional
  public ProjectView inviteJoin(String code) {
    long me = AuthContext.requireUserId();
    ProjectInvite invite = requireValidInvite(code);
    Project p =
        projects
            .findActive(invite.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    if (p.getIsPersonal() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_NO_INVITE_PERSONAL);
    }
    var existing = access.findOwner(p.getId(), me);
    if (existing.isPresent()) {
      p.setMyOwner(existing.get());
      return ProjectView.from(p);
    }
    projects.insertMember(
        IdGenerator.nextId(), p.getId(), me, ProjectAccessService.OWNER_MEMBER);
    p.setMyOwner(ProjectAccessService.OWNER_MEMBER);
    projectService.syncProjectGroup(p.getId());
    projectLogs.recordProject(p.getId(), 0L, "通过邀请链接加入项目", null);
    return ProjectView.from(p);
  }

  @Transactional
  public ProjectView transfer(long projectId, long userId) {
    long me = AuthContext.requireUserId();
    access.requireOwner(projectId, me);
    Project p = requireTeamProject(projectId);
    if (userId == me) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_TRANSFER_SELF);
    }
    access.requireMember(projectId, userId);
    if (!users.existsByUserId(userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
    projects.updateMemberOwner(projectId, me, ProjectAccessService.OWNER_MEMBER);
    projects.updateMemberOwner(projectId, userId, ProjectAccessService.OWNER_OWNER);
    p.setMyOwner(ProjectAccessService.OWNER_MEMBER);
    projectService.syncProjectGroup(projectId);
    projectLogs.recordProject(projectId, 0L, "移交项目给", Map.of("userId", userId));
    return ProjectView.from(p);
  }

  @Transactional
  public ProjectMemberChangeView addDeputy(long projectId, long userId) {
    long me = AuthContext.requireUserId();
    access.requireOwner(projectId, me);
    requireTeamProject(projectId);
    int role = access.requireMember(projectId, userId);
    if (role == ProjectAccessService.OWNER_OWNER) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_OWNER_NO_DEPUTY);
    }
    projects.updateMemberOwner(projectId, userId, ProjectAccessService.OWNER_ADMIN);
    projectLogs.recordProject(projectId, 0L, "任命项目管理员", Map.of("userId", userId));
    return new ProjectMemberChangeView(projectId, projects.listMemberUserIds(projectId));
  }

  @Transactional
  public ProjectMemberChangeView delDeputy(long projectId, long userId) {
    long me = AuthContext.requireUserId();
    access.requireOwner(projectId, me);
    requireTeamProject(projectId);
    int role = access.requireMember(projectId, userId);
    if (role != ProjectAccessService.OWNER_ADMIN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_NOT_ADMIN);
    }
    projects.updateMemberOwner(projectId, userId, ProjectAccessService.OWNER_MEMBER);
    projectLogs.recordProject(projectId, 0L, "罢免项目管理员", Map.of("userId", userId));
    return new ProjectMemberChangeView(projectId, projects.listMemberUserIds(projectId));
  }

  @Transactional
  public void exit(long projectId) {
    long me = AuthContext.requireUserId();
    int role = access.requireMember(projectId, me);
    if (role == ProjectAccessService.OWNER_OWNER) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_OWNER_EXIT);
    }
    projects.deleteMember(projectId, me);
    projectService.syncProjectGroup(projectId);
    projectLogs.recordProject(projectId, 0L, "退出项目", null);
  }

  @Transactional
  public ProjectView archive(long projectId, String type) {
    long me = AuthContext.requireUserId();
    access.requireOwner(projectId, me);
    Project p =
        projects
            .findActive(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    String t = type == null || type.isBlank() ? "add" : type.trim();
    if ("recovery".equals(t)) {
      if (p.getArchivedAt() == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_NOT_ARCHIVED);
      }
      projects.unarchive(projectId, me);
      if (archiveBridge != null) {
        archiveBridge.unarchiveByProject(projectId, me);
      }
      p.setArchivedAt(null);
      projectLogs.recordProject(projectId, 0L, "项目取消归档", null);
    } else if ("add".equals(t)) {
      if (p.getArchivedAt() != null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_ALREADY_ARCHIVED);
      }
      LocalDateTime now = LocalDateTime.now();
      projects.archive(projectId, me);
      if (archiveBridge != null) {
        archiveBridge.archiveByProject(projectId, me, now);
      }
      p.setArchivedAt(now);
      projectLogs.recordProject(projectId, 0L, "项目归档", null);
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_ARCHIVE_TYPE_INVALID);
    }
    p.setMyOwner(ProjectAccessService.OWNER_OWNER);
    return ProjectView.from(p);
  }

  @Transactional
  public void remove(long projectId) {
    long me = AuthContext.requireUserId();
    access.requireOwner(projectId, me);
    if (projects.findActive(projectId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
    }
    if (groupBridge != null) {
      groupBridge.disbandByLink(projectId);
    }
    projects.softDelete(projectId);
    projectLogs.recordProject(projectId, 0L, "删除项目", null);
  }

  private Project requireTeamProject(long projectId) {
    Project p =
        projects
            .findActive(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    if (p.getIsPersonal() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_PERSONAL_NO_COLLAB);
    }
    return p;
  }

  private ProjectInvite requireValidInvite(String code) {
    if (code == null || code.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_INVITE_EMPTY);
    }
    ProjectInvite invite =
        invites
            .findByCode(code.trim())
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_INVITE_INVALID));
    if (invite.getExpiredAt() != null && invite.getExpiredAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_INVITE_EXPIRED);
    }
    return invite;
  }

  private static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (String part : raw.split("[,，\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(part.trim()));
      } catch (NumberFormatException ex) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, part);
      }
    }
    return new ArrayList<>(ids);
  }
}
