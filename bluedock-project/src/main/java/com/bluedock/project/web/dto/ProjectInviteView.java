package com.bluedock.project.web.dto;

import java.time.LocalDateTime;

public record ProjectInviteView(String code, long projectId, LocalDateTime expiredAt) {}
