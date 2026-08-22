package com.bluedock.search.web.dto;

public record SearchHitView(String type, long id, String title, String snippet, long projectId) {}
