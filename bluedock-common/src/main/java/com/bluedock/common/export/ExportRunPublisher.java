package com.bluedock.common.export;

public interface ExportRunPublisher {
  void publish(ExportRunEvent event);
}
