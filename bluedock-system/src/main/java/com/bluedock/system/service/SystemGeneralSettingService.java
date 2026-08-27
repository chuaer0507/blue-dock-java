package com.bluedock.system.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 通用系统设置：`api/system/setting`。 */
@Service
public class SystemGeneralSettingService {
  public static final String SETTING_NAME = "systemSetting";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public SystemGeneralSettingService(
      SettingRepository settings,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  public Map<String, Object> get() {
    adminGuard.requireAdmin();
    Map<String, Object> out = load();
    if (writeGuard.isDisabled()) {
      out.put("writable", false);
    } else {
      out.put("writable", true);
    }
    return out;
  }

  public Map<String, Object> save(Map<String, Object> body) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    Map<String, Object> current = load();
    if (body != null) {
      current.putAll(body);
    }
    normalize(current);
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(current));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    current.put("writable", true);
    return current;
  }

  /** 供其它模块只读（如撤回时限）。 */
  public Map<String, Object> loadRaw() {
    return load();
  }

  public int messageRecallLimitMinutes() {
    return MeetingSettingService.intVal(load(), "messageRecallLimit", 0);
  }

  /** 匿名消息开关；默认 open。 */
  public boolean isAnonMessageOpen() {
    Map<String, Object> m = load();
    Object v = m.get("anonMessage");
    if (v == null) {
      return true;
    }
    return "open".equalsIgnoreCase(String.valueOf(v).trim());
  }

  /** 系统级任务 AI 自动分析开关；默认 open。 */
  public boolean isTaskAiAutoAnalyzeOpen() {
    Map<String, Object> m = load();
    Object v = m.get("taskAiAutoAnalyze");
    if (v == null) {
      return true;
    }
    return !"close".equalsIgnoreCase(String.valueOf(v).trim());
  }

  /** 未领取任务提醒开关；默认 close。 */
  public boolean isUnclaimedTaskReminderOpen() {
    return isOpenFlag(load().get("unclaimedTaskReminder"), false);
  }

  /**
   * 未领取任务提醒时刻（{@code HH:mm}，本地时钟按 JVM 默认时区）。默认 {@code 09:00}。
   */
  public String unclaimedTaskReminderTime() {
    Object v = load().get("unclaimedTaskReminderTime");
    if (v == null) {
      return "09:00";
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? "09:00" : s;
  }

  /** 部门负责人查看成员项目/任务的系统开关；默认 open。 */
  public boolean isDepartmentOwnerProjectViewOpen() {
    Map<String, Object> m = load();
    Object v = m.get("departmentOwnerProjectView");
    if (v == null) {
      return true;
    }
    return !"close".equalsIgnoreCase(String.valueOf(v).trim());
  }

  /** 私聊发言开关；默认 open（允许）。close=全局禁言私聊。 */
  public boolean isUserPrivateChatMuteOpen() {
    return isOpenFlag(load().get("userPrivateChatMute"), true);
  }

  /** 普通群发言开关；默认 open。close=全局禁言普通群。 */
  public boolean isUserGroupChatMuteOpen() {
    return isOpenFlag(load().get("userGroupChatMute"), true);
  }

  /** 全员群禁言；默认 close（不禁）。open=全员群禁言。 */
  public boolean isAllGroupMuteOpen() {
    return isOpenFlag(load().get("allGroupMute"), false);
  }

  private static boolean isOpenFlag(Object v, boolean defaultOpen) {
    if (v == null) {
      return defaultOpen;
    }
    return "open".equalsIgnoreCase(String.valueOf(v).trim());
  }

  private Map<String, Object> load() {
    Map<String, Object> out = defaults();
    settings
        .findSettingJson(SETTING_NAME)
        .ifPresent(
            json -> {
              try {
                out.putAll(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  private void normalize(Map<String, Object> m) {
    Object days = m.get("autoArchiveDay");
    if (days != null) {
      try {
        int d = Integer.parseInt(String.valueOf(days).trim());
        if (d < 1 || d > 100) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
        }
        m.put("autoArchiveDay", d);
      } catch (NumberFormatException e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
      }
    }
    Object rem = m.get("unclaimedTaskReminder");
    if (rem != null) {
      String s = String.valueOf(rem).trim().toLowerCase();
      if (!"open".equals(s) && !"close".equals(s)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
      }
      m.put("unclaimedTaskReminder", s);
    }
    Object time = m.get("unclaimedTaskReminderTime");
    if (time != null) {
      String s = String.valueOf(time).trim();
      if (!s.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
      }
      String[] parts = s.split(":");
      m.put(
          "unclaimedTaskReminderTime",
          String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
    }
  }

  private Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("passwordType", "simple");
    m.put("reg", "invite");
    m.put("inviteCode", "");
    m.put("messageRecallLimit", 0);
    m.put("messageEditLimit", 0);
    m.put("userPrivateChatMute", "open");
    m.put("userGroupChatMute", "open");
    m.put("allGroupMute", "close");
    m.put("autoArchive", "close");
    m.put("autoArchiveDay", 30);
    m.put("todoPermission", "allow");
    m.put("e2e", "close");
    m.put("taskAiAutoAnalyze", "open");
    m.put("unclaimedTaskReminder", "close");
    m.put("unclaimedTaskReminderTime", "09:00");
    m.put("departmentOwnerProjectView", "open");
    m.put("anonMessage", "open");
    return m;
  }
}
