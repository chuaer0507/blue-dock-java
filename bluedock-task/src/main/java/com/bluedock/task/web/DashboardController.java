package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.DashboardService;
import com.bluedock.task.web.dto.DashboardTeamStatsView;
import com.bluedock.task.web.dto.TaskView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final DashboardService dashboard;

  public DashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/team/stats")
  public ResultModel<DashboardTeamStatsView> teamStats(
      @RequestParam(required = false) Long departmentId) {
    return ResultModel.ok(dashboard.teamStats(departmentId));
  }

  @GetMapping("/team/tasks")
  public ResultModel<List<TaskView>> teamTasks(
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Long memberId,
      @RequestParam(required = false) Integer level,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(dashboard.teamTasks(departmentId, type, memberId, level, page, pageSize));
  }
}
