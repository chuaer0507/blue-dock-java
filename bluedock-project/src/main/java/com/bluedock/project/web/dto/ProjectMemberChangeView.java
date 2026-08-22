package com.bluedock.project.web.dto;

import java.util.List;

public record ProjectMemberChangeView(long projectId, List<Long> userIds) {}
