package com.bluedock.project.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.project.repo.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 供 bluedock-task 等模块校验项目成员关系。 */
@Service
public class ProjectAccessService {
  /** owner：0 成员 · 1 拥有者 · 2 管理员 */
  public static final int OWNER_MEMBER = 0;

  public static final int OWNER_OWNER = 1;
  public static final int OWNER_ADMIN = 2;

  private final ProjectRepository projects;

  public ProjectAccessService(ProjectRepository projects) {
    this.projects = projects;
  }

  public int requireMember(long projectId, long userId) {
    return projects
        .findMemberOwner(projectId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_DENIED));
  }

  /** 拥有者或管理员。 */
  public int requireManage(long projectId, long userId) {
    int owner = requireMember(projectId, userId);
    if (owner != OWNER_OWNER && owner != OWNER_ADMIN) {
      throw new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_MANAGE_REQUIRED);
    }
    return owner;
  }

  /** 仅拥有者。 */
  public int requireOwner(long projectId, long userId) {
    int owner = requireMember(projectId, userId);
    if (owner != OWNER_OWNER) {
      throw new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_OWNER_REQUIRED);
    }
    return owner;
  }

  public Optional<Integer> findOwner(long projectId, long userId) {
    return projects.findMemberOwner(projectId, userId);
  }

  /** 项目成员 userId 列表（供实时扇出等）。 */
  public List<Long> listMemberUserIds(long projectId) {
    return projects.listMemberUserIds(projectId);
  }
}
