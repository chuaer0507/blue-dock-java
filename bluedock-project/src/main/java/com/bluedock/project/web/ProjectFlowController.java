package com.bluedock.project.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.project.service.ProjectFlowService;
import com.bluedock.project.web.dto.ProjectFlowView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/flow")
public class ProjectFlowController {
  private final ProjectFlowService flows;

  public ProjectFlowController(ProjectFlowService flows) {
    this.flows = flows;
  }

  @GetMapping("/list")
  public ResultModel<List<ProjectFlowView>> list(@RequestParam long projectId) {
    return ResultModel.ok(flows.list(projectId));
  }

  @PostMapping("/save")
  public ResultModel<ProjectFlowView> save(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String items,
      @RequestBody(required = false) Map<String, Object> body) {
    long parentId = firstLong(projectId, body, "projectId");
    Long flowId = id;
    if ((flowId == null || flowId <= 0) && body != null) {
      long fromBody = asLong(body.get("id"));
      if (fromBody > 0) {
        flowId = fromBody;
      }
    }
    String n = name;
    if ((n == null || n.isBlank()) && body != null && body.get("name") != null) {
      n = String.valueOf(body.get("name"));
    }
    Object itemsRaw = items;
    if ((itemsRaw == null || (itemsRaw instanceof String s && s.isBlank())) && body != null) {
      itemsRaw = body.get("items");
    }
    return ResultModel.ok(flows.save(parentId, flowId, n, itemsRaw));
  }

  @GetMapping("/delete")
  public ResultModel<Void> delete(@RequestParam long id) {
    flows.delete(id);
    return ResultModel.ok();
  }

  private static long firstLong(Long a, Map<String, Object> body, String keyCamel) {
    if (a != null && a > 0) {
      return a;
    }
    if (body != null) {
      return asLong(body.get(keyCamel));
    }
    return 0L;
  }

  private static long asLong(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o != null) {
      try {
        return Long.parseLong(String.valueOf(o).trim());
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }
}
