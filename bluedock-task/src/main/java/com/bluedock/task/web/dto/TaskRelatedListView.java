package com.bluedock.task.web.dto;

import java.util.List;
import java.util.Map;

public record TaskRelatedListView(long taskId, List<Map<String, Object>> items) {}
