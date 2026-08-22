package com.bluedock.common.search;

public interface SearchIndexPublisher {
  void publish(SearchIndexEvent event);
}
