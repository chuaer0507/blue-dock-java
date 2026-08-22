package com.bluedock.task.column;

import com.bluedock.common.project.TaskColumnCascadeBridge;
import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskColumnCascadeBridgeImpl implements TaskColumnCascadeBridge {
  private final TaskRepository tasks;
  private final SearchIndexPublisher searchIndex;
  private final TaskGroupBridge groupBridge;

  public TaskColumnCascadeBridgeImpl(
      TaskRepository tasks,
      SearchIndexPublisher searchIndex,
      @Autowired(required = false) TaskGroupBridge groupBridge) {
    this.tasks = tasks;
    this.searchIndex = searchIndex;
    this.groupBridge = groupBridge;
  }

  @Override
  public void softDeleteByColumn(long projectId, long columnId, long userId) {
    List<TaskItem> mains = tasks.listByProject(projectId, columnId, true);
    for (TaskItem t : mains) {
      if (groupBridge != null) {
        groupBridge.disbandByLink(t.getId());
      }
      publishDelete(t);
    }
    tasks.softDeleteByColumn(projectId, columnId, userId);
  }

  private void publishDelete(TaskItem t) {
    SearchIndexEvent event =
        new SearchIndexEvent(
            UUID.randomUUID().toString().replace("-", ""),
            SearchIndexEvent.ACTION_DELETE,
            SearchIndexEvent.TYPE_TASK,
            t.getId(),
            t.getUserId(),
            t.getProjectId(),
            t.getName(),
            t.getDescription());
    searchIndex.publish(event);
  }
}
