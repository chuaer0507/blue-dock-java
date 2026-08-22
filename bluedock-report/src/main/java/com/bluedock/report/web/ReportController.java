package com.bluedock.report.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.report.service.ReportService;
import com.bluedock.report.web.dto.ReportView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportController {
  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/my")
  public ResultModel<List<ReportView>> my(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(reports.my(type, page, pageSize));
  }

  @GetMapping("/receive")
  public ResultModel<List<ReportView>> receive(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(reports.receive(type, status, page, pageSize));
  }

  @GetMapping("/detail")
  public ResultModel<ReportView> detail(
      @RequestParam(required = false) Long id, @RequestParam(required = false) String code) {
    return ResultModel.ok(reports.detail(id, code));
  }

  @GetMapping("/store")
  public ResultModel<ReportView> store(
      @RequestParam(required = false) Long id,
      @RequestParam String title,
      @RequestParam String type,
      @RequestParam String content,
      @RequestParam String receive,
      @RequestParam(required = false) Integer offset) {
    return ResultModel.ok(reports.store(id, title, type, content, receive, offset));
  }

  @GetMapping("/template")
  public ResultModel<Map<String, Object>> template(
      @RequestParam(required = false, defaultValue = "daily") String type,
      @RequestParam(required = false) Integer offset) {
    return ResultModel.ok(reports.template(type, offset));
  }

  @GetMapping("/mark")
  public ResultModel<Void> mark(
      @RequestParam long id, @RequestParam(required = false, defaultValue = "1") int read) {
    reports.mark(id, read);
    return ResultModel.ok();
  }

  @GetMapping("/read")
  public ResultModel<Void> read(@RequestParam String ids) {
    reports.read(ids);
    return ResultModel.ok();
  }

  @GetMapping("/unread")
  public ResultModel<Map<String, Object>> unread() {
    return ResultModel.ok(reports.unread());
  }

  @GetMapping("/lastSubmitter")
  public ResultModel<Map<String, Object>> lastSubmitter() {
    return ResultModel.ok(reports.lastSubmitter());
  }

  @GetMapping("/share")
  public ResultModel<Map<String, Object>> share(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String ids,
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String dialogIds,
      @RequestParam(required = false) String refresh) {
    String reportRaw =
        id != null && id > 0 ? String.valueOf(id) : (ids == null ? "" : ids);
    String dialogRaw =
        dialogId != null && dialogId > 0
            ? String.valueOf(dialogId)
            : (dialogIds == null ? "" : dialogIds);
    return ResultModel.ok(reports.share(reportRaw, dialogRaw, refresh));
  }

  @PostMapping("/analysisSave")
  public ResultModel<Map<String, Object>> analysisSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(reports.analysisSave(body));
  }

  @PostMapping("/aiGenerate")
  public ResultModel<Map<String, Object>> aiGenerate(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    Object type = b.get("type");
    Object content = b.get("content");
    return ResultModel.ok(
        reports.aiGenerate(
            type == null ? null : String.valueOf(type),
            content == null ? null : String.valueOf(content)));
  }
}
