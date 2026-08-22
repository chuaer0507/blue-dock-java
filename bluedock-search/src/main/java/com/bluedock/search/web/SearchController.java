package com.bluedock.search.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.search.service.SearchRebuildService;
import com.bluedock.search.service.SearchService;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
  private final SearchService search;
  private final SearchRebuildService rebuild;

  public SearchController(SearchService search, SearchRebuildService rebuild) {
    this.search = search;
    this.rebuild = rebuild;
  }

  @GetMapping("/contact")
  public ResultModel<List<SearchHitView>> contact(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.contact(key, take));
  }

  @GetMapping("/project")
  public ResultModel<List<SearchHitView>> project(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.project(key, take));
  }

  @GetMapping("/task")
  public ResultModel<List<SearchHitView>> task(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.task(key, take));
  }

  @GetMapping("/file")
  public ResultModel<List<SearchHitView>> file(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.file(key, take));
  }

  @GetMapping("/message")
  public ResultModel<List<SearchHitView>> message(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.message(key, take));
  }

  /** 管理员触发全量重建（异步，worker 消费）。 */
  @PostMapping("/rebuild")
  public ResultModel<Map<String, Object>> rebuild(
      @RequestParam(required = false) String types) {
    return ResultModel.ok(rebuild.start(types));
  }

  @GetMapping("/rebuild/status")
  public ResultModel<Map<String, Object>> rebuildStatus() {
    return ResultModel.ok(rebuild.status());
  }
}
