package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.ProjectSortService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectSortController {
  private final ProjectSortService sortService;

  public ProjectSortController(ProjectSortService sortService) {
    this.sortService = sortService;
  }

  /**
   * 看板排序。
   *
   * <p>参数：{@code projectId} · {@code onlyColumn} · {@code sort}（JSON 数组或请求体）。
   *
   * <pre>
   * [{ "id": columnId, "task": [taskId, ...] }, ...]
   * </pre>
   */
  @PostMapping("/sort")
  public ResultModel<Void> sort(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) Integer onlyColumn,
      @RequestParam(required = false) String sort,
      @RequestBody(required = false) Map<String, Object> body) {
    long parentId = firstLong(projectId, body == null ? null : body.get("projectId"));
    boolean onlyCol = firstBool(onlyColumn, body);
    Object sortRaw = sort;
    if ((sortRaw == null || (sortRaw instanceof String s && s.isBlank())) && body != null) {
      sortRaw = body.get("sort");
    }
    sortService.sort(parentId, onlyCol, sortRaw);
    return ResultModel.ok();
  }

  private static long firstLong(Long a, Object fromBody) {
    if (a != null && a > 0) {
      return a;
    }
    if (fromBody instanceof Number n) {
      return n.longValue();
    }
    if (fromBody != null) {
      try {
        return Long.parseLong(String.valueOf(fromBody).trim());
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }

  private static boolean firstBool(Integer a, Map<String, Object> body) {
    if (a != null) {
      return a != 0;
    }
    if (body != null) {
      Object v = body.get("onlyColumn");
      if (v instanceof Number n) {
        return n.intValue() != 0;
      }
      if (v instanceof Boolean bool) {
        return bool;
      }
      if (v != null) {
        String s = String.valueOf(v).trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
      }
    }
    return false;
  }
}
