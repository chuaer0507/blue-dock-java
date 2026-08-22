package com.bluedock.task.web.dto;

import java.util.List;

public record TaskTemplateSearchPage(List<TaskTemplateView> items, PageMeta meta) {

  public record PageMeta(int page, int pageSize, long totalSize, int totalPage) {}
}
