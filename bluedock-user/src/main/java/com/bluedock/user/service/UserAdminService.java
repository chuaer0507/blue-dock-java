package com.bluedock.user.service;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.notify.SystemMsgDmBridge;
import com.bluedock.common.user.UserDisableHandoverBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.web.dto.UserAdminView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员：会员列表 / 创建用户 / operation。 */
@Service
public class UserAdminService {
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final int PASS_MIN = 6;
  private static final int PASS_MAX = 32;
  private static final int EMAIL_MAX = 32;

  private final UserAccountRepository users;
  private final AdminGuard adminGuard;
  private final WirePasswordResolver passwords;
  private final PasswordEncoder passwordEncoder;
  private final ObjectProvider<LicenseCapacity> licenseCapacity;
  private final List<UserDisableHandoverBridge> handovers;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;
  private final ObjectProvider<SystemMsgDmBridge> systemMsg;

  public UserAdminService(
      UserAccountRepository users,
      AdminGuard adminGuard,
      WirePasswordResolver passwords,
      PasswordEncoder passwordEncoder,
      ObjectProvider<LicenseCapacity> licenseCapacity,
      List<UserDisableHandoverBridge> handovers,
      ObjectProvider<NotifySendPublisher> notifyPublisher,
      ObjectProvider<SystemMsgDmBridge> systemMsg) {
    this.users = users;
    this.adminGuard = adminGuard;
    this.passwords = passwords;
    this.passwordEncoder = passwordEncoder;
    this.licenseCapacity = licenseCapacity;
    this.handovers = handovers == null ? List.of() : handovers;
    this.notifyPublisher = notifyPublisher;
    this.systemMsg = systemMsg;
  }

  public Map<String, Object> lists(
      String key, Integer page, Integer pageSize, Integer isBot) {
    adminGuard.requireAdmin();
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(100, Math.max(1, pageSize));
    boolean includeBot = isBot != null && isBot != 0;
    String keyword = key == null ? "" : key.trim();
    int total = users.countForAdmin(keyword, includeBot);
    List<UserAdminView> list =
        users.listForAdmin(keyword, includeBot, size, (p - 1) * size).stream()
            .map(UserAdminView::from)
            .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("total", total);
    out.put("page", p);
    out.put("pageSize", size);
    return out;
  }

  @Transactional
  public UserPublicView createUser(
      String emailRaw,
      String nicknameRaw,
      String passwordCipher,
      String keyId,
      String professionRaw,
      String identityRaw) {
    adminGuard.requireAdmin();
    String email = normalizeEmail(emailRaw);
    if (users.existsByEmail(email)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_TAKEN);
    }
    String nickname = nicknameRaw == null ? "" : nicknameRaw.trim();
    if (nickname.length() < 2 || nickname.length() > 20) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_NICKNAME_LENGTH);
    }
    String plain = passwords.requirePlain(keyId, passwordCipher);
    if (plain.length() < PASS_MIN || plain.length() > PASS_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_LENGTH);
    }
    String profession = professionRaw == null ? "" : professionRaw.trim();
    if (!profession.isEmpty() && (profession.length() < 2 || profession.length() > 20)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PROFESSION_LENGTH);
    }
    String identity = normalizeIdentity(identityRaw);
    LicenseCapacity cap = licenseCapacity.getIfAvailable();
    if (cap != null) {
      cap.assertCanAddUser();
    }
    UserAccount u = new UserAccount();
    u.setUserId(IdGenerator.nextId());
    u.setEmail(email);
    u.setNickname(nickname);
    u.setProfession(profession);
    u.setIdentity(identity);
    u.setPassword(passwordEncoder.encode(plain));
    u.setIsBot(0);
    u.setUserImage("");
    u.setEmailVerify(1);
    u.setMustChangePassword(1);
    users.insert(u);
    return UserPublicView.from(u);
  }

  /**
   * 管理员操作用户。
   *
   * <p>支持：{@code setAdmin}/{@code clearAdmin}/{@code setTemporary}/{@code clearTemporary}/
   * {@code disable}/{@code enable}。{@code disable} 须传 {@code handoverUserId}。
   */
  @Transactional
  public UserAdminView operation(String typeRaw, Long userId) {
    return operation(typeRaw, userId, null);
  }

  @Transactional
  public UserAdminView operation(String typeRaw, Long userId, Long handoverUserId) {
    adminGuard.requireAdmin();
    if (userId == null || userId <= 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
    String type = typeRaw == null ? "" : typeRaw.trim();
    if (type.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_TYPE_INVALID);
    }
    UserAccount target =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (target.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_BOT_DENIED);
    }
    if (hasTag(target.getIdentity(), "system") || userId == 1L) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_SYSTEM_DENIED);
    }
    long me = AuthContext.requireUserId();
    Set<String> tags = parseTags(target.getIdentity());
    switch (type) {
      case "setAdmin" -> {
        if (hasTag(target.getIdentity(), "temporary")) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_TEMPORARY_ADMIN);
        }
        tags.add("admin");
        applyIdentity(target, tags);
      }
      case "clearAdmin" -> {
        if (userId == me) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_SELF_DENIED);
        }
        tags.remove("admin");
        applyIdentity(target, tags);
      }
      case "setTemporary" -> {
        tags.add("temporary");
        tags.remove("admin");
        applyIdentity(target, tags);
      }
      case "clearTemporary" -> {
        tags.remove("temporary");
        applyIdentity(target, tags);
      }
      case "disable" -> {
        if (userId == me) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_SELF_DENIED);
        }
        long handoverId = requireHandoverUser(userId, handoverUserId);
        for (UserDisableHandoverBridge bridge : handovers) {
          bridge.handover(userId, handoverId);
        }
        tags.add("disable");
        tags.remove("admin");
        LocalDateTime now = LocalDateTime.now();
        applyIdentity(target, tags);
        users.updateDisableAt(userId, now);
        target.setDisableAt(now);
        notifyHandoverComplete(target, handoverId, me);
      }
      case "enable" -> {
        tags.remove("disable");
        applyIdentity(target, tags);
        users.updateDisableAt(userId, null);
        target.setDisableAt(null);
      }
      default -> throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_TYPE_INVALID);
    }
    return UserAdminView.from(target);
  }

  private long requireHandoverUser(long disabledUserId, Long handoverUserId) {
    if (handoverUserId == null || handoverUserId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_HANDOVER_REQUIRED);
    }
    if (handoverUserId == disabledUserId) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_HANDOVER_INVALID);
    }
    UserAccount handover =
        users
            .findByUserId(handoverUserId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_HANDOVER_INVALID));
    if (handover.getIsBot() == 1
        || handover.getDisableAt() != null
        || hasTag(handover.getIdentity(), "disable")
        || hasTag(handover.getIdentity(), "bot")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_OP_HANDOVER_INVALID);
    }
    return handoverUserId;
  }

  /** 交接完成后：桌面通知交接人；system-msg 私聊交接人。事务提交后投递。 */
  private void notifyHandoverComplete(UserAccount disabled, long handoverUserId, long operatorId) {
    String name =
        disabled.getNickname() == null || disabled.getNickname().isBlank()
            ? disabled.getEmail()
            : disabled.getNickname().trim();
    String title = "账号交接完成";
    String body =
        "用户「"
            + name
            + "」已设为离职，其项目负责人、任务负责与部门负责人等归属已交接给你。";
    String dm = title + "\n" + body;
    NotifySendPublisher pub = notifyPublisher.getIfAvailable();
    if (pub != null) {
      pub.publish(
          new NotifySendEvent(
              "handover-" + IdGenerator.nextId(),
              NotifySendEvent.CHANNEL_DESKTOP,
              List.of(handoverUserId),
              title,
              body,
              Map.of(
                  "kind",
                  "userHandover",
                  "disabledUserId",
                  disabled.getUserId(),
                  "handoverUserId",
                  handoverUserId,
                  "operatorUserId",
                  operatorId)));
    }
    SystemMsgDmBridge dmBridge = systemMsg.getIfAvailable();
    if (dmBridge != null) {
      dmBridge.sendDm(handoverUserId, dm);
    }
  }

  private void applyIdentity(UserAccount target, Set<String> tags) {
    String identity = formatTags(tags);
    users.updateIdentity(target.getUserId(), identity);
    target.setIdentity(identity);
  }

  private static String normalizeEmail(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_INVALID);
    }
    String email = raw.trim().toLowerCase(Locale.ROOT);
    if (email.length() > EMAIL_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_LENGTH);
    }
    if (!EMAIL.matcher(email).matches()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_INVALID);
    }
    return email;
  }

  /** 接受 JSON 数组字符串、逗号分隔，或空 → `[]`；禁止自行写入 system。 */
  static String normalizeIdentity(String raw) {
    if (raw == null || raw.isBlank()) {
      return "[]";
    }
    String t = raw.trim();
    List<String> tags = new ArrayList<>();
    if (t.startsWith("[")) {
      t = t.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
    }
    for (String part : t.split("[,，\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      String tag = part.trim().toLowerCase(Locale.ROOT);
      if ("system".equals(tag) || "bot".equals(tag) || "disable".equals(tag)) {
        continue;
      }
      if ("admin".equals(tag) || "ldap".equals(tag) || "temporary".equals(tag)) {
        if (!tags.contains(tag)) {
          tags.add(tag);
        }
      }
    }
    return formatTags(new LinkedHashSet<>(tags));
  }

  static boolean hasTag(String identity, String tag) {
    return parseTags(identity).contains(tag.toLowerCase(Locale.ROOT));
  }

  static Set<String> parseTags(String identity) {
    LinkedHashSet<String> tags = new LinkedHashSet<>();
    if (identity == null || identity.isBlank()) {
      return tags;
    }
    String t = identity.trim();
    if (t.startsWith("[")) {
      t = t.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
    }
    for (String part : t.split("[,，\\s]+")) {
      if (!part.isBlank()) {
        tags.add(part.trim().toLowerCase(Locale.ROOT));
      }
    }
    return tags;
  }

  static String formatTags(Set<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder("[");
    int i = 0;
    for (String tag : tags) {
      if (i++ > 0) {
        sb.append(',');
      }
      sb.append('"').append(tag).append('"');
    }
    sb.append(']');
    return sb.toString();
  }
}
