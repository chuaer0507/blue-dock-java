package com.bluedock.assistant.web.dto;

import java.util.Map;

public record AssistantSessionView(
    String id,
    String title,
    Object responses,
    Map<String, String> images,
    String sceneKey,
    long createdAt,
    long updatedAt) {}
