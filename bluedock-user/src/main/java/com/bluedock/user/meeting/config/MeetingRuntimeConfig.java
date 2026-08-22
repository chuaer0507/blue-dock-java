package com.bluedock.user.meeting.config;

import com.bluedock.system.service.MeetingSettingService;
import java.util.Map;
import org.springframework.stereotype.Component;

/** YAML 默认 + bluedock_settings.meetingSetting 覆盖。 */
@Component
public class MeetingRuntimeConfig {
  private final MeetingProperties yaml;
  private final MeetingSettingService settings;

  public MeetingRuntimeConfig(MeetingProperties yaml, MeetingSettingService settings) {
    this.yaml = yaml;
    this.settings = settings;
  }

  public boolean isEnabled() {
    Map<String, Object> db = settings.loadRaw();
    if (db.containsKey("enabled") || db.containsKey("open")) {
      return MeetingSettingService.openFlag(db, "enabled", "open", yaml.isEnabled());
    }
    return yaml.isEnabled();
  }

  public String getAppId() {
    return prefer(MeetingSettingService.str(settings.loadRaw(), "appId"), yaml.getAppId());
  }

  public String getAppCertificate() {
    return prefer(
        MeetingSettingService.str(settings.loadRaw(), "appCertificate"), yaml.getAppCertificate());
  }

  public String getApiKey() {
    return prefer(MeetingSettingService.str(settings.loadRaw(), "apiKey"), yaml.getApiKey());
  }

  public String getApiSecret() {
    return prefer(MeetingSettingService.str(settings.loadRaw(), "apiSecret"), yaml.getApiSecret());
  }

  public boolean isAllowDevToken() {
    Map<String, Object> db = settings.loadRaw();
    if (db.containsKey("allowDevToken")) {
      return MeetingSettingService.openFlag(db, "allowDevToken", yaml.isAllowDevToken());
    }
    return yaml.isAllowDevToken();
  }

  public boolean isAllowCloseWithoutRest() {
    Map<String, Object> db = settings.loadRaw();
    if (db.containsKey("allowCloseWithoutRest")) {
      return MeetingSettingService.openFlag(
          db, "allowCloseWithoutRest", yaml.isAllowCloseWithoutRest());
    }
    return yaml.isAllowCloseWithoutRest();
  }

  public int getCloseIdleMinutes() {
    Map<String, Object> db = settings.loadRaw();
    if (db.containsKey("closeIdleMinutes")) {
      return MeetingSettingService.intVal(db, "closeIdleMinutes", yaml.getCloseIdleMinutes());
    }
    return yaml.getCloseIdleMinutes();
  }

  public String getChannelSalt() {
    return prefer(MeetingSettingService.str(settings.loadRaw(), "channelSalt"), yaml.getChannelSalt());
  }

  public String getShareBaseUrl() {
    return prefer(
        MeetingSettingService.str(settings.loadRaw(), "shareBaseUrl"), yaml.getShareBaseUrl());
  }

  public int getShareTtlHours() {
    Map<String, Object> db = settings.loadRaw();
    if (db.containsKey("shareTtlHours")) {
      return MeetingSettingService.intVal(db, "shareTtlHours", yaml.getShareTtlHours());
    }
    return yaml.getShareTtlHours();
  }

  private static String prefer(String db, String yaml) {
    return db == null || db.isBlank() ? (yaml == null ? "" : yaml) : db;
  }
}
