package com.bluedock.project.handover;

import com.bluedock.common.user.UserDisableHandoverBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectService;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 离职交接 — 项目：负责人迁给交接人；离职用户退出全部项目成员；同步项目群。
 */
@Component
@Order(10)
public class ProjectUserDisableHandoverBridge implements UserDisableHandoverBridge {
  private final ProjectRepository projects;
  private final ProjectService projectService;

  public ProjectUserDisableHandoverBridge(
      ProjectRepository projects, ProjectService projectService) {
    this.projects = projects;
    this.projectService = projectService;
  }

  @Override
  @Transactional
  public void handover(long fromUserId, long toUserId) {
    if (fromUserId == toUserId) {
      return;
    }
    for (Long projectId : projects.listOwnedProjectIds(fromUserId)) {
      transferOwner(projectId, fromUserId, toUserId);
    }
    List<Long> memberProjects = projects.listMemberProjectIds(fromUserId);
    for (Long projectId : memberProjects) {
      projects.deleteMember(projectId, fromUserId);
      projectService.syncProjectGroup(projectId);
    }
  }

  private void transferOwner(long projectId, long fromUserId, long toUserId) {
    Project p = projects.findActive(projectId).orElse(null);
    if (p == null) {
      return;
    }
    ensureMember(projectId, toUserId, ProjectAccessService.OWNER_OWNER);
    projects.updateMemberOwner(projectId, toUserId, ProjectAccessService.OWNER_OWNER);
    if (projects.findMemberOwner(projectId, fromUserId).isPresent()) {
      projects.updateMemberOwner(projectId, fromUserId, ProjectAccessService.OWNER_MEMBER);
    }
    if (p.getIsPersonal() == 1) {
      // 交接人已有个人项目时，将被交接个人项目降为团队项目再移交
      if (toUserId != p.getUserId() && projects.countPersonalForUser(toUserId) > 0) {
        projects.updateIsPersonal(projectId, 0);
      }
      projects.updateProjectCreator(projectId, toUserId);
    }
    projectService.syncProjectGroup(projectId);
  }

  private void ensureMember(long projectId, long userId, int owner) {
    if (projects.findMemberOwner(projectId, userId).isPresent()) {
      return;
    }
    projects.insertMember(IdGenerator.nextId(), projectId, userId, owner);
  }
}
