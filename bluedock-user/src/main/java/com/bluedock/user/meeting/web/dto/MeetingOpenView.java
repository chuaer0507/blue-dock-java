package com.bluedock.user.meeting.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MeetingOpenView(
    long id,
    String meetingId,
    String name,
    String channel,
    long userId,
    String appId,
    long agoraUserId,
    String token,
    String nickname,
    String userImage,
    List<Long> invitedUserIds,
    List<Map<String, Object>> messages,
    LocalDateTime createdAt,
    LocalDateTime endAt) {}
