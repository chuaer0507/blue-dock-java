package com.bluedock.task.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskFile;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskFileRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.TaskFileView;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskFileService {
  private final TaskFileRepository files;
  private final TaskRepository tasks;
  private final ProjectAccessService access;
  private final ObjectProvider<BrowseRecorder> browseRecorder;

  public TaskFileService(
      TaskFileRepository files,
      TaskRepository tasks,
      ProjectAccessService access,
      ObjectProvider<BrowseRecorder> browseRecorder) {
    this.files = files;
    this.tasks = tasks;
    this.access = access;
    this.browseRecorder = browseRecorder;
  }

  public List<TaskFileView> list(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem task = requireTask(taskId);
    access.requireMember(task.getProjectId(), userId);
    return files.listByTask(taskId).stream().map(TaskFileView::from).toList();
  }

  public Map<String, Object> detail(long fileId, String onlyUpdateAt) {
    long userId = AuthContext.requireUserId();
    TaskFile f =
        files
            .findActive(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    TaskItem task = requireTask(f.getTaskId());
    access.requireMember(task.getProjectId(), userId);
    if ("yes".equalsIgnoreCase(onlyUpdateAt)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", f.getId());
      m.put("updatedAt", f.getUpdatedAt() == null ? null : f.getUpdatedAt().toString());
      return m;
    }
    BrowseRecorder recorder = browseRecorder.getIfAvailable();
    if (recorder != null) {
      recorder.recordTaskFile(userId, f.getId(), f.getTaskId());
    }
    return toMap(f);
  }

  @Transactional
  public Map<String, Object> delete(long fileId) {
    long userId = AuthContext.requireUserId();
    TaskFile f =
        files
            .findActive(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    TaskItem task = requireTask(f.getTaskId());
    access.requireMember(task.getProjectId(), userId);
    files.softDelete(fileId);
    return toMap(f);
  }

  @Transactional
  public Map<String, Object> down(long fileId) {
    long userId = AuthContext.requireUserId();
    TaskFile f =
        files
            .findActive(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    TaskItem task = requireTask(f.getTaskId());
    access.requireMember(task.getProjectId(), userId);
    files.bumpDownload(fileId);
    f.setDownloadCount(f.getDownloadCount() + 1);
    Map<String, Object> m = toMap(f);
    m.put("url", f.getPath());
    return m;
  }

  /** 上传合并后写入附件元数据（供 upload 场景调用）。 */
  @Transactional
  public TaskFileView attach(
      long taskId, String name, long size, String extension, String path, String thumbnail) {
    long userId = AuthContext.requireUserId();
    TaskItem task = requireTask(taskId);
    access.requireMember(task.getProjectId(), userId);
    if (task.getParentId() > 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NAME_INVALID);
    }
    LocalDateTime now = LocalDateTime.now();
    TaskFile f = new TaskFile();
    f.setId(IdGenerator.nextId());
    f.setProjectId(task.getProjectId());
    f.setTaskId(taskId);
    f.setName(n);
    f.setSize(Math.max(size, 0));
    f.setExtension(extension == null ? "" : extension.trim());
    f.setPath(path == null ? "" : path.trim());
    f.setThumbnail(thumbnail == null ? "" : thumbnail.trim());
    f.setUserId(userId);
    f.setDownloadCount(0);
    f.setCreatedAt(now);
    f.setUpdatedAt(now);
    files.insert(f);
    return TaskFileView.from(f);
  }

  private TaskItem requireTask(long taskId) {
    return tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
  }

  private static Map<String, Object> toMap(TaskFile f) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", f.getId());
    m.put("projectId", f.getProjectId());
    m.put("taskId", f.getTaskId());
    m.put("name", f.getName());
    m.put("size", f.getSize());
    m.put("extension", f.getExtension());
    m.put("path", f.getPath());
    m.put("thumbnail", f.getThumbnail());
    m.put("userId", f.getUserId());
    m.put("downloadCount", f.getDownloadCount());
    m.put("createdAt", f.getCreatedAt() == null ? null : f.getCreatedAt().toString());
    m.put("updatedAt", f.getUpdatedAt() == null ? null : f.getUpdatedAt().toString());
    return m;
  }
}
