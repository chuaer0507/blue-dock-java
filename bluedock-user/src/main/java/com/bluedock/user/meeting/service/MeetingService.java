package com.bluedock.user.meeting.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.meeting.MeetingInviteBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.user.meeting.agora.AgoraAccessToken;
import com.bluedock.user.meeting.config.MeetingRuntimeConfig;
import com.bluedock.user.meeting.domain.Meeting;
import com.bluedock.user.meeting.repo.MeetingRepository;
import com.bluedock.user.meeting.web.dto.MeetingOpenView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
  private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private final MeetingRepository meetings;
  private final UserAccountRepository users;
  private final StringRedisTemplate redis;
  private final MeetingRuntimeConfig props;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<MeetingInviteBridge> inviteBridge;
  private final SecureRandom random = new SecureRandom();

  public MeetingService(
      MeetingRepository meetings,
      UserAccountRepository users,
      StringRedisTemplate redis,
      MeetingRuntimeConfig props,
      ObjectMapper objectMapper,
      ObjectProvider<MeetingInviteBridge> inviteBridge) {
    this.meetings = meetings;
    this.users = users;
    this.redis = redis;
    this.props = props;
    this.objectMapper = objectMapper;
    this.inviteBridge = inviteBridge;
  }

  @Transactional
  public MeetingOpenView open(
      String type,
      String meetingId,
      String name,
      String userIds,
      String shareKey,
      String username,
      String userImage) {
    requireEnabled();
    String ty = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    boolean guestJoin = "join".equals(ty) && shareKey != null && !shareKey.isBlank();
    Long loginUserId = AuthContext.get() == null ? null : AuthContext.get().userId();

    if (!guestJoin && loginUserId == null) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.MEETING_AUTH_REQUIRED);
    }
    if (guestJoin) {
      Map<String, Object> share = loadShare(shareKey.trim());
      if (share == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_SHARE_EXPIRED);
      }
    }

    Meeting meeting;
    List<Long> invited = List.of();
    List<Map<String, Object>> messages = List.of();
    if ("join".equals(ty)) {
      String mid = meetingId == null ? "" : meetingId.replace(" ", "").trim();
      meeting =
          meetings
              .findByMeetingId(mid)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.MEETING_NOT_FOUND));
      if (meeting.getEndAt() != null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_ENDED);
      }
      meetings.touch(mid);
    } else if ("create".equals(ty)) {
      meeting = createMeeting(loginUserId, name);
      invited = parseUserIds(userIds);
      invited.removeIf(u -> u.equals(loginUserId));
      messages = dispatchInvites(meeting, loginUserId, invited);
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_TYPE_INVALID);
    }

    int agoraUserId = allocateAgoraUserId(loginUserId);
    String token = buildToken(meeting.getChannel(), agoraUserId);

    String nickname;
    String image;
    if (guestJoin) {
      nickname = username == null || username.isBlank() ? "Guest" : username.trim();
      image = userImage == null ? "" : userImage.trim();
    } else {
      UserAccount account =
          users
              .findByUserId(loginUserId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.MEETING_AUTH_REQUIRED));
      nickname = account.getNickname() == null ? "" : account.getNickname();
      image = account.getUserImage() == null ? "" : account.getUserImage();
    }

    cacheTourist(String.valueOf(agoraUserId), nickname, image);
    return new MeetingOpenView(
        meeting.getId(),
        meeting.getMeetingId(),
        meeting.getName(),
        meeting.getChannel(),
        meeting.getUserId(),
        props.getAppId() == null ? "" : props.getAppId(),
        agoraUserId,
        token,
        nickname,
        image,
        invited,
        messages,
        meeting.getCreatedAt(),
        meeting.getEndAt());
  }

  public Map<String, Object> link(String meetingId, String shareKey) {
    requireEnabled();
    boolean hasShare = shareKey != null && !shareKey.isBlank() && loadShare(shareKey.trim()) != null;
    if (!hasShare && AuthContext.get() == null) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.MEETING_AUTH_REQUIRED);
    }
    Meeting meeting =
        meetings
            .findByMeetingId(meetingId == null ? "" : meetingId.trim())
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.MEETING_NOT_FOUND));
    String code = createShareCode(meeting);
    String base = props.getShareBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String url = base + "/meeting/" + meeting.getMeetingId() + "/" + code;
    return Map.of("url", url, "shareKey", code, "meetingId", meeting.getMeetingId());
  }

  public Map<String, Object> tourist(String touristId) {
    if (touristId == null || touristId.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_TOURIST_MISSING);
    }
    String raw = redis.opsForValue().get(RedisKeys.meetingTourist(touristId.trim()));
    if (raw == null) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.MEETING_TOURIST_MISSING);
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<>() {});
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.MEETING_TOURIST_MISSING);
    }
  }

  public Map<String, Object> invitation(String meetingId, String userIds) {
    requireEnabled();
    long me = AuthContext.requireUserId();
    Meeting meeting =
        meetings
            .findByMeetingId(meetingId == null ? "" : meetingId.trim())
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.MEETING_NOT_FOUND));
    if (meeting.getEndAt() != null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_ENDED);
    }
    List<Long> ids = parseUserIds(userIds);
    List<Long> ok = new ArrayList<>();
    for (Long inviteeUserId : ids) {
      if (inviteeUserId != me && users.existsByUserId(inviteeUserId)) {
        ok.add(inviteeUserId);
      }
    }
    List<Map<String, Object>> messages = dispatchInvites(meeting, me, ok);
    Map<String, Object> data = meetingPayload(meeting);
    data.put("invitedUserIds", ok);
    data.put("messages", messages);
    return data;
  }

  private List<Map<String, Object>> dispatchInvites(
      Meeting meeting, long inviterUserId, List<Long> invitees) {
    MeetingInviteBridge bridge = inviteBridge.getIfAvailable();
    if (bridge == null || invitees == null || invitees.isEmpty()) {
      return List.of();
    }
    return bridge.sendInvites(meetingPayload(meeting), inviterUserId, invitees);
  }

  private static Map<String, Object> meetingPayload(Meeting meeting) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", meeting.getId());
    data.put("meetingId", meeting.getMeetingId());
    data.put("name", meeting.getName());
    data.put("channel", meeting.getChannel());
    data.put("userId", meeting.getUserId());
    data.put(
        "createdAt", meeting.getCreatedAt() == null ? null : meeting.getCreatedAt().toString());
    data.put("endAt", meeting.getEndAt() == null ? null : meeting.getEndAt().toString());
    return data;
  }

  private Meeting createMeeting(long userId, String name) {
    String mid = randomMeetingId();
    String channel = "BlueDock:" + md5(mid + props.getChannelSalt()).substring(16);
    String title = name == null || name.isBlank() ? defaultName(userId) : name.trim();
    LocalDateTime now = LocalDateTime.now();
    Meeting m = new Meeting();
    m.setId(IdGenerator.nextId());
    m.setMeetingId(mid);
    m.setName(title);
    m.setChannel(channel);
    m.setUserId(userId);
    m.setCreatedAt(now);
    m.setUpdatedAt(now);
    meetings.insert(m);
    return m;
  }

  private String defaultName(long userId) {
    return users
        .findByUserId(userId)
        .map(u -> (u.getNickname() == null || u.getNickname().isBlank() ? "User" : u.getNickname()) + " 发起的会议")
        .orElse("Meeting");
  }

  private String buildToken(String channel, int agoraUserId) {
    String cert = props.getAppCertificate();
    String appId = props.getAppId();
    if (cert != null && !cert.isBlank() && appId != null && !appId.isBlank()) {
      try {
        return AgoraAccessToken.build(appId, cert, channel, agoraUserId, 24 * 3600);
      } catch (Exception e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_TOKEN_FAILED);
      }
    }
    if (props.isAllowDevToken()) {
      return "dev." + channel + "." + agoraUserId;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_CONFIG);
  }

  private void requireEnabled() {
    if (!props.isEnabled()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_DISABLED);
    }
  }

  private String createShareCode(Meeting meeting) {
    String random = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(12));
    String code =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((meeting.getMeetingId() + random).getBytes(StandardCharsets.UTF_8));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", meeting.getId());
    payload.put("meetingId", meeting.getMeetingId());
    payload.put("channel", meeting.getChannel());
    try {
      redis
          .opsForValue()
          .set(
              RedisKeys.meetingShare(code),
              objectMapper.writeValueAsString(payload),
              Duration.ofHours(props.getShareTtlHours()));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_TOKEN_FAILED);
    }
    return code;
  }

  private Map<String, Object> loadShare(String code) {
    String raw = redis.opsForValue().get(RedisKeys.meetingShare(code));
    if (raw == null) {
      return null;
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<>() {});
    } catch (Exception e) {
      return null;
    }
  }

  private void cacheTourist(String agoraUserId, String nickname, String userImage) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("agoraUserId", agoraUserId);
    data.put("nickname", nickname);
    data.put("userImage", userImage);
    try {
      redis
          .opsForValue()
          .set(
              RedisKeys.meetingTourist(agoraUserId),
              objectMapper.writeValueAsString(data),
              Duration.ofHours(props.getShareTtlHours()));
    } catch (Exception ignored) {
      // best-effort
    }
  }

  private static int allocateAgoraUserId(Long userId) {
    if (userId == null) {
      return 800_000_000 + ThreadLocalRandom.current().nextInt(99_999_999);
    }
    int suffix = (int) (Math.abs(userId) % 1_000_000L);
    int prefix = ThreadLocalRandom.current().nextInt(100, 999);
    return prefix * 1_000_000 + suffix;
  }

  private String randomMeetingId() {
    StringBuilder sb = new StringBuilder(11);
    for (int i = 0; i < 11; i++) {
      sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
    }
    return sb.toString();
  }

  private byte[] randomBytes(int n) {
    byte[] b = new byte[n];
    random.nextBytes(b);
    return b;
  }

  private static String md5(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<Long> parseUserIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return new ArrayList<>();
    }
    List<Long> ids = new ArrayList<>();
    for (String part : raw.split("[,，\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(part.trim()));
      } catch (NumberFormatException ex) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, part);
      }
    }
    return ids;
  }
}
