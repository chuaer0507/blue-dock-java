package com.bluedock.task.web.dto;

import java.util.List;
import java.util.Map;

public record DashboardTeamStatsView(
    int uncompleted,
    int overdue,
    int soon,
    int weekCompleted,
    List<Long> memberUserIds,
    List<Long> projectIds,
    Map<String, Integer> priority) {}
