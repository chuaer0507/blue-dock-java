package com.bluedock.project.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.model.ResultModel;
import com.bluedock.project.service.ProjectTagService;
import com.bluedock.project.web.dto.ProjectTagView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/tag")
public class ProjectTagController {
  private final ProjectTagService tags;
  private final ObjectMapper objectMapper;

  public ProjectTagController(ProjectTagService tags, ObjectMapper objectMapper) {
    this.tags = tags;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/list")
  public ResultModel<List<ProjectTagView>> list(@RequestParam long projectId) {
    return ResultModel.ok(tags.list(projectId));
  }

  @PostMapping("/save")
  public ResultModel<ProjectTagView> save(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String color,
      @RequestBody(required = false) Map<String, Object> body) {
    long parentId = firstLong(projectId, body, "projectId");
    Long tagId = id;
    if ((tagId == null || tagId <= 0) && body != null) {
      long fromBody = asLong(body.get("id"));
      if (fromBody > 0) {
        tagId = fromBody;
      }
    }
    String n = name;
    if ((n == null || n.isBlank()) && body != null && body.get("name") != null) {
      n = String.valueOf(body.get("name"));
    }
    String c = color;
    if (c == null && body != null && body.get("color") != null) {
      c = String.valueOf(body.get("color"));
    }
    return ResultModel.ok(tags.save(parentId, tagId, n, c));
  }

  @PostMapping("/sort")
  public ResultModel<Void> sort(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) String list,
      @RequestBody(required = false) Map<String, Object> body) {
    long parentId = firstLong(projectId, body, "projectId");
    Object raw = null;
    if (body != null) {
      raw = body.get("list");
    }
    if (raw == null && list != null && !list.isBlank()) {
      try {
        raw = objectMapper.readValue(list, List.class);
      } catch (Exception e) {
        raw = List.of();
      }
    }
    tags.sort(parentId, raw instanceof List<?> l ? l : List.of());
    return ResultModel.ok();
  }

  @GetMapping("/delete")
  public ResultModel<Void> delete(@RequestParam long id) {
    tags.delete(id);
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
