package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.TaskContentService;
import com.bluedock.task.service.TaskFileService;
import com.bluedock.task.service.TaskRelationService;
import com.bluedock.task.service.TaskService;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentHistoryPage;
import com.bluedock.task.web.dto.TaskFileView;
import com.bluedock.task.web.dto.TaskRelatedListView;
import com.bluedock.task.web.dto.TaskView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/task")
public class TaskController {
  private final TaskService tasks;
  private final TaskFileService taskFiles;
  private final TaskRelationService relations;
  private final TaskContentService contents;

  public TaskController(
      TaskService tasks,
      TaskFileService taskFiles,
      TaskRelationService relations,
      TaskContentService contents) {
    this.tasks = tasks;
    this.taskFiles = taskFiles;
    this.relations = relations;
    this.contents = contents;
  }

  @GetMapping("/lists")
  public ResultModel<List<TaskView>> lists(
      @RequestParam long projectId,
      @RequestParam(required = false) Long columnId,
      @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
    return ResultModel.ok(tasks.lists(projectId, columnId, includeArchived));
  }

  @GetMapping("/easyLists")
  public ResultModel<List<Map<String, Object>>> easyLists(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String userIds,
      @RequestParam(required = false) String timeRange,
      @RequestParam(required = false) Long excludeTaskId,
      @RequestParam(required = false) Integer limit) {
    String owners = userIds != null && !userIds.isBlank() ? userIds : userId;
    return ResultModel.ok(tasks.easyLists(owners, timeRange, excludeTaskId, limit));
  }

  @GetMapping("/one")
  public ResultModel<TaskView> one(@RequestParam long taskId) {
    return ResultModel.ok(tasks.one(taskId));
  }

  @GetMapping("/dialog")
  public ResultModel<TaskView> dialog(@RequestParam long taskId) {
    return ResultModel.ok(tasks.dialog(taskId));
  }

  @PostMapping("/add")
  public ResultModel<TaskView> add(
      @RequestParam long projectId,
      @RequestParam(required = false) Long columnId,
      @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) Integer visibility,
      @RequestParam(required = false) String visibilityUserIds,
      @RequestParam(required = false) Long ownerUserId,
      @RequestParam(required = false) String startAt,
      @RequestParam(required = false) String endAt,
      @RequestParam(required = false) Integer loop,
      @RequestParam(required = false) Long templateId) {
    return ResultModel.ok(
        tasks.add(
            projectId,
            columnId,
            name,
            description,
            color,
            visibility,
            visibilityUserIds,
            ownerUserId,
            startAt,
            endAt,
            loop,
            templateId));
  }

  @PostMapping("/update")
  public ResultModel<TaskView> update(
      @RequestParam long taskId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) Long columnId,
      @RequestParam(required = false) Integer visibility,
      @RequestParam(required = false) String visibilityUserIds,
      @RequestParam(required = false) Integer complete,
      @RequestParam(required = false) String startAt,
      @RequestParam(required = false) String endAt,
      @RequestParam(required = false) Integer priorityLevel,
      @RequestParam(required = false) String priorityName,
      @RequestParam(required = false) String priorityColor,
      @RequestParam(required = false) String content,
      @RequestParam(required = false) String owner,
      @RequestParam(required = false) String assist,
      @RequestParam(required = false) Long flowItemId,
      @RequestParam(required = false) String tagIds,
      @RequestParam(required = false) Integer loop) {
    return ResultModel.ok(
        tasks.update(
            taskId,
            name,
            description,
            color,
            columnId,
            visibility,
            visibilityUserIds,
            complete,
            startAt,
            endAt,
            priorityLevel,
            priorityName,
            priorityColor,
            content,
            owner,
            assist,
            flowItemId,
            tagIds,
            loop));
  }

  @GetMapping("/flow")
  public ResultModel<Map<String, Object>> flow(
      @RequestParam long taskId,
      @RequestParam(required = false) Long flowItemId) {
    return ResultModel.ok(tasks.flow(taskId, flowItemId));
  }

  @GetMapping("/resetFromLog")
  public ResultModel<TaskView> resetFromLog(@RequestParam long id) {
    return ResultModel.ok(tasks.resetFromLog(id));
  }

  @GetMapping("/addSubtask")
  public ResultModel<TaskView> addSubtask(
      @RequestParam long taskId,
      @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) Long ownerUserId) {
    return ResultModel.ok(tasks.addSubtask(taskId, name, description, ownerUserId));
  }

  @GetMapping("/subtaskData")
  public ResultModel<List<TaskView>> subtaskData(@RequestParam long taskId) {
    return ResultModel.ok(tasks.subtaskData(taskId));
  }

  @GetMapping("/archived")
  public ResultModel<TaskView> archived(
      @RequestParam long taskId,
      @RequestParam(required = false, defaultValue = "false") boolean follow) {
    return ResultModel.ok(tasks.archive(taskId, follow));
  }

  @GetMapping("/remove")
  public ResultModel<Void> remove(@RequestParam long taskId) {
    tasks.remove(taskId);
    return ResultModel.ok();
  }

  @GetMapping("/move")
  public ResultModel<List<TaskView>> move(
      @RequestParam long taskId,
      @RequestParam long projectId,
      @RequestParam long columnId,
      @RequestParam(required = false) Integer completed) {
    return ResultModel.ok(tasks.move(taskId, projectId, columnId, completed));
  }

  @GetMapping("/upgrade")
  public ResultModel<TaskView> upgrade(@RequestParam long taskId) {
    return ResultModel.ok(tasks.upgrade(taskId));
  }

  @PostMapping("/copy")
  public ResultModel<TaskView> copy(
      @RequestParam long taskId,
      @RequestParam long projectId,
      @RequestParam long columnId,
      @RequestParam(required = false) Long ownerUserId,
      @RequestParam(required = false) Integer completed) {
    return ResultModel.ok(tasks.copy(taskId, projectId, columnId, ownerUserId, completed));
  }

  @GetMapping("/related")
  public ResultModel<TaskRelatedListView> related(@RequestParam long taskId) {
    return ResultModel.ok(relations.list(taskId));
  }

  /** 手动建立双向关联（消息 @# 亦可走同一 Service）。 */
  @PostMapping("/related")
  public ResultModel<Map<String, Object>> relatedAdd(
      @RequestParam long taskId, @RequestParam long relatedTaskId) {
    return ResultModel.ok(relations.add(taskId, relatedTaskId));
  }

  @PostMapping("/related/delete")
  public ResultModel<Void> relatedDelete(
      @RequestParam long taskId, @RequestParam long relatedTaskId) {
    relations.delete(taskId, relatedTaskId);
    return ResultModel.ok();
  }

  @GetMapping("/content")
  public ResultModel<Object> content(
      @RequestParam long taskId, @RequestParam(required = false) Long historyId) {
    return ResultModel.ok(contents.get(taskId, historyId));
  }

  @GetMapping("/contentHistory")
  public ResultModel<TaskContentHistoryPage> contentHistory(
      @RequestParam long taskId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(contents.history(taskId, page, pageSize));
  }

  @GetMapping("/calendar")
  public ResultModel<List<TaskView>> calendar(
      @RequestParam String start, @RequestParam String end) {
    return ResultModel.ok(tasks.calendar(start, end));
  }

  @GetMapping("/files")
  public ResultModel<List<TaskFileView>> files(@RequestParam long taskId) {
    return ResultModel.ok(taskFiles.list(taskId));
  }

  @GetMapping("/fileDetail")
  public ResultModel<Map<String, Object>> fileDetail(
      @RequestParam long fileId,
      @RequestParam(required = false, defaultValue = "no") String onlyUpdateAt) {
    return ResultModel.ok(taskFiles.detail(fileId, onlyUpdateAt));
  }

  @GetMapping("/fileDelete")
  public ResultModel<Map<String, Object>> fileDelete(@RequestParam long fileId) {
    return ResultModel.ok(taskFiles.delete(fileId));
  }

  @GetMapping("/fileDownload")
  public ResultModel<Map<String, Object>> fileDown(@RequestParam long fileId) {
    return ResultModel.ok(taskFiles.down(fileId));
  }
}
