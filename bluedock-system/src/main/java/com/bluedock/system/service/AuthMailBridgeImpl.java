package com.bluedock.system.service;

import com.bluedock.common.auth.AuthMailBridge;
import com.bluedock.common.notify.mail.SmtpMailClient;
import com.bluedock.common.notify.mail.SmtpMailClient.SmtpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthMailBridgeImpl implements AuthMailBridge {
  private static final Logger log = LoggerFactory.getLogger(AuthMailBridgeImpl.class);

  private final EmailSettingService emailSettings;

  public AuthMailBridgeImpl(EmailSettingService emailSettings) {
    this.emailSettings = emailSettings;
  }

  @Override
  public boolean send(String to, String subject, String body) {
    SmtpConfig cfg = emailSettings.smtpConfig();
    if (cfg == null || !cfg.configured()) {
      return false;
    }
    try {
      SmtpMailClient.send(cfg, to, subject, body);
      return true;
    } catch (Exception e) {
      log.warn("auth mail send failed to {}: {}", to, e.toString());
      throw new IllegalStateException("mail send failed", e);
    }
  }
}
