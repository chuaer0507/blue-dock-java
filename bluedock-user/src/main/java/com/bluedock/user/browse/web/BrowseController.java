package com.bluedock.user.browse.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.browse.service.BrowseService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class BrowseController {
  private final BrowseService browse;

  public BrowseController(BrowseService browse) {
    this.browse = browse;
  }

  @GetMapping("/task/browse")
  public ResultModel<List<Map<String, Object>>> taskBrowse(
      @RequestParam(required = false) Integer limit) {
    return ResultModel.ok(browse.taskBrowse(limit));
  }

  @GetMapping("/task/browseSave")
  public ResultModel<Map<String, Object>> taskBrowseSave(@RequestParam Long taskId) {
    return ResultModel.ok(browse.taskBrowseSave(taskId));
  }

  @PostMapping("/task/browseClean")
  public ResultModel<Map<String, Object>> taskBrowseClean(
      @RequestParam(required = false) Integer keepCount) {
    return ResultModel.ok(browse.taskBrowseClean(keepCount));
  }

  @GetMapping("/recent/browse")
  public ResultModel<Map<String, Object>> recentBrowse(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(browse.recentBrowse(type, page, pageSize));
  }

  @PostMapping("/recent/delete")
  public ResultModel<Map<String, Object>> recentDelete(@RequestParam Long id) {
    return ResultModel.ok(browse.recentDelete(id));
  }
}
