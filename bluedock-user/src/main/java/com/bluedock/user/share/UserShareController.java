package com.bluedock.user.share;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.service.UserAnnualReportService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserShareController {
  private final UserShareListService shareList;
  private final UserAnnualReportService annualReport;

  public UserShareController(UserShareListService shareList, UserAnnualReportService annualReport) {
    this.shareList = shareList;
    this.annualReport = annualReport;
  }

  /**
   * 分享选择器。{@code type}=file|text；{@code parentId} 下钻目录；无 {@code parentId} 时返回根入口 +
   * 会话候选（可 {@code key} 搜索）。
   */
  @GetMapping("/share/list")
  public ResultModel<List<Map<String, Object>>> shareList(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String key,
      @RequestParam(required = false) Long parentId) {
    return ResultModel.ok(shareList.list(type, key, parentId));
  }

  /** 个人年度报告；默认当前年，可选 {@code year}。 */
  @GetMapping("/annual/report")
  public ResultModel<Map<String, Object>> annualReport(
      @RequestParam(required = false) Integer year) {
    return ResultModel.ok(annualReport.report(year));
  }
}
