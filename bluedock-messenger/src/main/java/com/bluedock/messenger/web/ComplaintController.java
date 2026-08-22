package com.bluedock.messenger.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.messenger.complaint.ComplaintService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/complaint")
public class ComplaintController {
  private final ComplaintService complaints;

  public ComplaintController(ComplaintService complaints) {
    this.complaints = complaints;
  }

  @GetMapping("/lists")
  public ResultModel<Map<String, Object>> lists(
      @RequestParam(required = false) Integer type,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    Integer size = pageSize;
    return ResultModel.ok(complaints.lists(type, status, page, size));
  }

  @PostMapping("/submit")
  public ResultModel<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
    long dialogId = asLong(body.get("dialogId"));
    int type = asInt(body.get("type"), 0);
    String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> images =
        body.get("images") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    return ResultModel.ok(complaints.submit(dialogId, type, reason, images));
  }

  @PostMapping("/action")
  public ResultModel<Map<String, Object>> action(@RequestBody Map<String, Object> body) {
    long id = asLong(body.get("id"));
    String type = body.get("type") == null ? "" : String.valueOf(body.get("type"));
    return ResultModel.ok(complaints.action(id, type));
  }

  private static long asLong(Object v) {
    if (v instanceof Number n) {
      return n.longValue();
    }
    if (v == null) {
      return 0L;
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static int asInt(Object v, int def) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
