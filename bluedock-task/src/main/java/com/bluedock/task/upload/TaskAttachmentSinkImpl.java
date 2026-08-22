package com.bluedock.task.upload;

import com.bluedock.common.upload.TaskAttachmentSink;
import com.bluedock.task.service.TaskFileService;
import com.bluedock.task.web.dto.TaskFileView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskAttachmentSinkImpl implements TaskAttachmentSink {
  private final TaskFileService taskFiles;

  public TaskAttachmentSinkImpl(TaskFileService taskFiles) {
    this.taskFiles = taskFiles;
  }

  @Override
  public Map<String, Object> save(
      long taskId, String name, long size, String extension, String path, String thumbnail) {
    TaskFileView v = taskFiles.attach(taskId, name, size, extension, path, thumbnail);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", v.id());
    m.put("projectId", v.projectId());
    m.put("taskId", v.taskId());
    m.put("name", v.name());
    m.put("size", v.size());
    m.put("extension", v.extension());
    m.put("path", v.path());
    m.put("thumbnail", v.thumbnail());
    m.put("userId", v.userId());
    m.put("downloadCount", v.downloadCount());
    m.put("createdAt", v.createdAt() == null ? null : v.createdAt().toString());
    m.put("updatedAt", v.updatedAt() == null ? null : v.updatedAt().toString());
    return m;
  }
}
