package com.bluedock.common.notify.mail;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** 按运行时 SMTP 配置发信（配置来自 bluedock_settings，非 YAML）。 */
public final class SmtpMailClient {
  private SmtpMailClient() {}

  public static void send(SmtpConfig cfg, String to, String subject, String body) throws Exception {
    if (cfg == null || !cfg.configured()) {
      throw new IllegalStateException("smtp not configured");
    }
    if (to == null || to.isBlank()) {
      throw new IllegalArgumentException("to empty");
    }
    Properties props = new Properties();
    props.put("mail.smtp.host", cfg.host());
    props.put("mail.smtp.port", String.valueOf(cfg.port()));
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "15000");
    if (cfg.ssl()) {
      props.put("mail.smtp.ssl.enable", "true");
      props.put("mail.smtp.socketFactory.port", String.valueOf(cfg.port()));
      props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    } else {
      props.put("mail.smtp.starttls.enable", "true");
    }
    Session session =
        Session.getInstance(
            props,
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.username(), cfg.password());
              }
            });
    MimeMessage msg = new MimeMessage(session);
    String fromAddr = cfg.fromAddress().isBlank() ? cfg.username() : cfg.fromAddress();
    if (cfg.fromAlias() == null || cfg.fromAlias().isBlank()) {
      msg.setFrom(new InternetAddress(fromAddr));
    } else {
      msg.setFrom(new InternetAddress(fromAddr, cfg.fromAlias(), StandardCharsets.UTF_8.name()));
    }
    msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to.trim()));
    msg.setSubject(subject == null ? "" : subject, StandardCharsets.UTF_8.name());
    msg.setText(body == null ? "" : body, StandardCharsets.UTF_8.name());
    Transport.send(msg);
  }

  public record SmtpConfig(
      String host,
      int port,
      String username,
      String password,
      boolean ssl,
      String fromAlias,
      String fromAddress) {

    public boolean configured() {
      return host != null
          && !host.isBlank()
          && username != null
          && !username.isBlank()
          && password != null
          && !password.isBlank();
    }
  }
}
