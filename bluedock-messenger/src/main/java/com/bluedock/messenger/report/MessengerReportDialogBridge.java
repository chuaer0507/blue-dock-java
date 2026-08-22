package com.bluedock.messenger.report;

import com.bluedock.common.report.ReportDialogBridge;
import com.bluedock.messenger.service.DialogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessengerReportDialogBridge implements ReportDialogBridge {
  private static final Logger log = LoggerFactory.getLogger(MessengerReportDialogBridge.class);

  private final DialogService dialogs;

  public MessengerReportDialogBridge(DialogService dialogs) {
    this.dialogs = dialogs;
  }

  @Override
  public long sendText(long dialogId, String text) {
    try {
      return dialogs.sendText(dialogId, text, null).id();
    } catch (Exception e) {
      log.warn("report share to dialog {} failed: {}", dialogId, e.toString());
      return 0L;
    }
  }
}
