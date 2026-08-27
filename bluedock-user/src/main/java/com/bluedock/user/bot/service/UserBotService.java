package com.bluedock.user.bot.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.bot.SystemUserBots;
import com.bluedock.user.bot.repo.UserBotRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBotService {
  private static final int MAX_BOTS = 50;
  private static final Set<String> EVENT_OPTIONS =
      Set.of("message", "dialogOpen", "memberJoin", "memberLeave");

  private final UserBotRepository bots;
  private final UserAccountRepository users;
  private final AdminGuard adminGuard;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;

  public UserBotService(
      UserBotRepository bots,
      UserAccountRepository users,
      AdminGuard adminGuard,
      PasswordEncoder passwordEncoder,
      ObjectMapper objectMapper) {
    this.bots = bots;
    this.users = users;
    this.adminGuard = adminGuard;
    this.passwordEncoder = passwordEncoder;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> list() {
    long userId = AuthContext.requireUserId();
    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, Object> row : bots.listOwned(userId)) {
      list.add(toView(row, String.valueOf(row.get("email"))));
    }
    return Map.of("list", list);
  }

  public Map<String, Object> info(Long id) {
    long userId = AuthContext.requireUserId();
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    UserAccount botUser =
        users
            .findByUserId(id)
            .filter(u -> u.getIsBot() == 1)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.BOT_NOT_FOUND));
    var owned = bots.findOwned(userId, id);
    if (owned.isEmpty()) {
      if (SystemUserBots.isSystemEmail(botUser.getEmail())) {
        adminGuard.requireAdmin();
      } else {
        throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.BOT_NOT_OWNER);
      }
      return systemView(botUser);
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", botUser.getUserId());
    row.put("name", botUser.getNickname());
    row.put("avatar", botUser.getUserImage() == null ? "" : botUser.getUserImage());
    row.put("clearDay", owned.get().get("clearDay"));
    row.put("webhookUrl", owned.get().get("webhookUrl"));
    row.put("webhookEvents", owned.get().get("webhookEvents"));
    return toView(row, botUser.getEmail());
  }

  @Transactional
  public Map<String, Object> edit(
      Long id,
      String name,
      String avatar,
      Integer clearDay,
      String webhookUrl,
      Object webhookEvents) {
    long userId = AuthContext.requireUserId();
    UserAccount botUser;
    boolean isNew = id == null || id <= 0;
    if (isNew) {
      if (bots.countOwned(userId) >= MAX_BOTS) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_LIMIT);
      }
      String n = name == null ? "" : name.trim();
      if (n.length() < 2 || n.length() > 20) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NAME_INVALID);
      }
      botUser = createBotUser(userId, n);
      bots.insert(userId, botUser.getUserId(), 90, "", eventsJson(List.of("message")));
    } else {
      botUser =
          users
              .findByUserId(id)
              .filter(u -> u.getIsBot() == 1)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.BOT_NOT_FOUND));
      var owned = bots.findOwned(userId, id);
      if (owned.isEmpty()) {
        if (SystemUserBots.isSystemEmail(botUser.getEmail())) {
          adminGuard.requireAdmin();
        } else {
          throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.BOT_NOT_OWNER);
        }
      }
      if (name != null) {
        String n = name.trim();
        if (n.length() < 2 || n.length() > 20) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NAME_INVALID);
        }
        botUser.setNickname(n);
      }
      if (avatar != null) {
        botUser.setUserImage(avatar.trim());
      }
      users.updateProfile(botUser);
      if (owned.isPresent()) {
        Integer day = clearDay == null ? null : Math.min(999, Math.max(1, clearDay));
        String events =
            webhookEvents == null ? null : eventsJson(normalizeEvents(webhookEvents, false));
        bots.update(userId, id, day, webhookUrl, events);
      }
    }
    return info(botUser.getUserId());
  }

  @Transactional
  public void delete(Long id, String remark) {
    long userId = AuthContext.requireUserId();
    String r = remark == null ? "" : remark.trim();
    if (r.isEmpty() || r.length() > 255) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_DELETE_REMARK);
    }
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    UserAccount botUser =
        users
            .findByUserId(id)
            .filter(u -> u.getIsBot() == 1)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.BOT_NOT_FOUND));
    if (SystemUserBots.isSystemEmail(botUser.getEmail())) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.BOT_SYSTEM_DELETE);
    }
    if (bots.findOwned(userId, id).isEmpty()) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.BOT_NOT_OWNER);
    }
    bots.delete(userId, id);
    // 软禁用机器人账号
    botUser.setNickname(botUser.getNickname() + "(deleted)");
    users.updateProfile(botUser);
  }

  private UserAccount createBotUser(long ownerId, String name) {
    UserAccount u = new UserAccount();
    u.setUserId(IdGenerator.nextId());
    u.setEmail("user-bot-" + ownerId + "-" + UUID.randomUUID().toString().substring(0, 8) + "@bot.user");
    u.setNickname(name);
    u.setUserImage("");
    u.setIdentity("[]");
    u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
    u.setIsBot(1);
    users.insert(u);
    return u;
  }

  private Map<String, Object> systemView(UserAccount botUser) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", botUser.getUserId());
    m.put("name", botUser.getNickname());
    m.put("avatar", botUser.getUserImage() == null ? "" : botUser.getUserImage());
    m.put("clearDay", 0);
    m.put("webhookUrl", "");
    m.put("webhookEvents", List.of("message"));
    m.put("systemName", SystemUserBots.nameOfEmail(botUser.getEmail()));
    return m;
  }

  private Map<String, Object> toView(Map<String, Object> row, String email) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", row.get("id"));
    m.put("name", row.get("name"));
    m.put("avatar", row.get("avatar") == null ? "" : row.get("avatar"));
    m.put("clearDay", row.get("clearDay") == null ? 90 : row.get("clearDay"));
    m.put("webhookUrl", row.get("webhookUrl") == null ? "" : row.get("webhookUrl"));
    m.put("webhookEvents", parseEvents(row.get("webhookEvents")));
    m.put("systemName", SystemUserBots.nameOfEmail(email));
    return m;
  }

  private List<String> parseEvents(Object raw) {
    if (raw == null) {
      return List.of("message");
    }
    try {
      if (raw instanceof String s) {
        if (s.isBlank()) {
          return List.of("message");
        }
        return normalizeEvents(objectMapper.readValue(s, new TypeReference<List<String>>() {}), true);
      }
      return normalizeEvents(raw, true);
    } catch (Exception e) {
      return List.of("message");
    }
  }

  private List<String> normalizeEvents(Object events, boolean fallback) {
    List<String> list = new ArrayList<>();
    if (events instanceof List<?> raw) {
      for (Object o : raw) {
        String s = canonicalizeEvent(String.valueOf(o));
        if (EVENT_OPTIONS.contains(s)) {
          list.add(s);
        }
      }
    } else if (events != null) {
      String s = canonicalizeEvent(String.valueOf(events));
      if (EVENT_OPTIONS.contains(s)) {
        list.add(s);
      }
    }
    if (list.isEmpty() && fallback) {
      return List.of("message");
    }
    return List.copyOf(list);
  }

  /** 0.x：旧 snake 事件名迁到 camelCase。 */
  private static String canonicalizeEvent(String raw) {
    if (raw == null) {
      return "";
    }
    return switch (raw.trim()) {
      case "dialog_open" -> "dialogOpen";
      case "member_join" -> "memberJoin";
      case "member_leave" -> "memberLeave";
      default -> raw.trim();
    };
  }

  private String eventsJson(List<String> events) {
    try {
      return objectMapper.writeValueAsString(events);
    } catch (Exception e) {
      return "[\"message\"]";
    }
  }
}
