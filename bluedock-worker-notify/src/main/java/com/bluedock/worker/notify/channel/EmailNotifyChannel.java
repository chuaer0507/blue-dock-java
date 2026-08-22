package com.bluedock.worker.notify.channel;

import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.notify.mail.EmailSettingMaps;
import com.bluedock.common.notify.mail.SmtpMailClient;
import com.bluedock.common.notify.mail.SmtpMailClient.SmtpConfig;
import com.bluedock.worker.notify.repo.NotifySettingRepository;
import com.bluedock.worker.notify.repo.NotifyUserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotifyChannel {
  private static final Logger log = LoggerFactory.getLogger(EmailNotifyChannel.class);

  private final NotifySettingRepository settings;
  private final NotifyUserRepository users;

  public EmailNotifyChannel(NotifySettingRepository settings, NotifyUserRepository users) {
    this.settings = settings;
    this.users = users;
  }

  public void deliver(NotifySendEvent event) {
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("smtpHost", "");
    defaults.put("smtpPort", "465");
    defaults.put("smtpUsername", "");
    defaults.put("smtpPassword", "");
    defaults.put("smtpSsl", "open");
    defaults.put("fromAlias", "BlueDock");
    defaults.put("fromAddress", "");
    defaults.put("ignoreAddr", "");
    Map<String, Object> cfg = settings.load(NotifySettingNames.EMAIL, defaults);
    SmtpConfig smtp = EmailSettingMaps.toSmtp(cfg);
    if (!smtp.configured()) {
      log.debug("email skip: smtp not configured");
      return;
    }
    Set<String> ignore = new HashSet<>(EmailSettingMaps.parseIgnore(cfg));
    Map<Long, String> emails = users.emailsByUserIds(event.userIds());
    if (emails.isEmpty()) {
      log.debug("email skip: no recipients");
      return;
    }
    String subject = event.title() == null ? "" : event.title();
    String body = event.body() == null ? "" : event.body();
    boolean anySent = false;
    for (Map.Entry<Long, String> e : emails.entrySet()) {
      String addr = e.getValue();
      if (ignore.contains(addr.toLowerCase(Locale.ROOT))) {
        log.debug("email ignore user_id={} addr={}", e.getKey(), addr);
        continue;
      }
      try {
        SmtpMailClient.send(smtp, addr, subject, body);
        anySent = true;
        log.info("email sent user_id={} to={}", e.getKey(), addr);
      } catch (Exception ex) {
        log.warn("email fail user_id={} to={}: {}", e.getKey(), addr, ex.toString());
      }
    }
    if (anySent) {
      markUnreadDigestReads(event.data());
    }
  }

  /** 未读汇总：发信成功后置 {@code bluedock_dialog_message_reads.email=1}。 */
  private void markUnreadDigestReads(Map<String, Object> data) {
    if (data == null || !"unreadDigest".equals(String.valueOf(data.get("kind")))) {
      return;
    }
    Object raw = data.get("messageReadIds");
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return;
    }
    List<Long> ids = new ArrayList<>();
    for (Object o : list) {
      if (o instanceof Number n) {
        ids.add(n.longValue());
      } else if (o != null) {
        try {
          ids.add(Long.parseLong(String.valueOf(o).trim()));
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
    }
    if (!ids.isEmpty()) {
      users.markMessageReadsEmailed(ids);
    }
  }
}
