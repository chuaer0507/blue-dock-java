package com.bluedock.system.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.config.SystemProperties;
import com.bluedock.system.license.LicenseOnlineClient;
import com.bluedock.system.license.MachineFingerprint;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线授权：默认 {@code local}（邮箱验证码 + 本机落盘）；{@code remote} 调官方商店 HTTP。
 *
 * <p>remote 路径约定见 {@code docs/infra/license.md}。
 */
@Service
public class OnlineLicenseService {
  private static final Logger log = LoggerFactory.getLogger(OnlineLicenseService.class);
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration PENDING_TTL = Duration.ofMinutes(30);
  private static final Duration TRIAL_MARK_TTL = Duration.ofDays(400);

  private static final String PATH_EMAIL_SEND = "/v1/license/email/send";
  private static final String PATH_LOGIN = "/v1/license/login";
  private static final String PATH_CONFIRM = "/v1/license/confirm";
  private static final String PATH_TRIAL = "/v1/license/trial";
  private static final String PATH_REFRESH = "/v1/license/refresh";
  private static final String PATH_LOGOUT = "/v1/license/logout";

  private final LicenseService licenses;
  private final SystemProperties props;
  private final StringRedisTemplate redis;
  private final LicenseOnlineClient onlineClient;

  public OnlineLicenseService(
      LicenseService licenses,
      SystemProperties props,
      StringRedisTemplate redis,
      LicenseOnlineClient onlineClient) {
    this.licenses = licenses;
    this.props = props;
    this.redis = redis;
    this.onlineClient = onlineClient;
  }

  public Map<String, Object> sendEmail(String emailRaw) {
    String email = normalizeEmail(emailRaw);
    if (isLocal()) {
      String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
      redis.opsForValue().set(RedisKeys.licenseOnlineCode(email), code, CODE_TTL);
      log.info("license online code prepared for {}", email);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("sent", true);
      out.put("email", email);
      out.put("expiresIn", CODE_TTL.toSeconds());
      out.put("devCode", code);
      return out;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    Map<String, Object> remote = onlineClient.post(requireRemoteUrl(), PATH_EMAIL_SEND, body);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sent", true);
    out.put("email", email);
    Object expires = first(remote, "expiresIn", "expires_in");
    out.put("expiresIn", expires != null ? asLong(expires, CODE_TTL.toSeconds()) : CODE_TTL.toSeconds());
    return out;
  }

  public Map<String, Object> login(String emailRaw, String codeRaw) {
    String email = normalizeEmail(emailRaw);
    String code = codeRaw == null ? "" : codeRaw.trim();
    if (isLocal()) {
      String expect = redis.opsForValue().get(RedisKeys.licenseOnlineCode(email));
      if (expect == null || expect.isBlank() || !expect.equals(code)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_CODE_INVALID);
      }
      redis.delete(RedisKeys.licenseOnlineCode(email));
      String token = UUID.randomUUID().toString().replace("-", "");
      redis.opsForValue().set(RedisKeys.licenseOnlinePending(token), email, PENDING_TTL);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("token", token);
      out.put("email", email);
      out.put("needConfirm", true);
      out.put("expiresIn", PENDING_TTL.toSeconds());
      return out;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("code", code);
    Map<String, Object> remote = onlineClient.post(requireRemoteUrl(), PATH_LOGIN, body);
    Map<String, Object> data = unwrapData(remote);
    String token = str(first(data, "token"));
    if (token.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_UNAVAILABLE);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("email", str(first(data, "email")).isEmpty() ? email : str(first(data, "email")));
    out.put("needConfirm", true);
    Object expires = first(data, "expiresIn", "expires_in");
    out.put(
        "expiresIn", expires != null ? asLong(expires, PENDING_TTL.toSeconds()) : PENDING_TTL.toSeconds());
    return out;
  }

  public Map<String, Object> confirm(String tokenRaw) {
    String token = tokenRaw == null ? "" : tokenRaw.trim();
    if (token.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_PENDING_INVALID);
    }
    String sn = MachineFingerprint.machineSn(props.getMachineSn());
    List<String> machineMacAddresses = MachineFingerprint.macAddresses();
    if (isLocal()) {
      String email = redis.opsForValue().get(RedisKeys.licenseOnlinePending(token));
      if (email == null || email.isBlank()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_PENDING_INVALID);
      }
      redis.delete(RedisKeys.licenseOnlinePending(token));
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("license", "online:" + email);
      payload.put("people", 10);
      payload.put("sn", sn);
      payload.put("macAddresses", machineMacAddresses);
      payload.put("expiredAt", LocalDate.now().plusYears(1).toString());
      payload.put("online", true);
      payload.put("onlineEmail", email);
      return licenses.applyOnline(payload);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("token", token);
    body.put("sn", sn);
    body.put("macAddresses", machineMacAddresses);
    Map<String, Object> remote = onlineClient.post(requireRemoteUrl(), PATH_CONFIRM, body);
    return licenses.applyOnline(toLicensePayload(remote, true));
  }

  public Map<String, Object> trial(String emailRaw) {
    String email = emailRaw == null || emailRaw.isBlank() ? "" : normalizeEmail(emailRaw);
    String sn = MachineFingerprint.machineSn(props.getMachineSn());
    List<String> machineMacAddresses = MachineFingerprint.macAddresses();
    if (isLocal()) {
      Boolean first =
          redis
              .opsForValue()
              .setIfAbsent(RedisKeys.licenseOnlineTrial(sn), "1", TRIAL_MARK_TTL);
      if (Boolean.FALSE.equals(first)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_TRIAL_USED);
      }
      int days = Math.min(60, Math.max(1, props.getLicenseTrialDays()));
      int people = Math.min(3, Math.max(1, props.getLicenseTrialPeople()));
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("license", "trial:" + (email.isEmpty() ? sn : email));
      payload.put("people", people);
      payload.put("sn", sn);
      payload.put("macAddresses", machineMacAddresses);
      payload.put("expiredAt", LocalDate.now().plusDays(days).toString());
      payload.put("online", true);
      payload.put("onlineEmail", email);
      payload.put("trialClaimed", true);
      return licenses.applyOnline(payload);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    if (!email.isEmpty()) {
      body.put("email", email);
    }
    body.put("sn", sn);
    body.put("macAddresses", machineMacAddresses);
    Map<String, Object> remote = onlineClient.post(requireRemoteUrl(), PATH_TRIAL, body);
    Map<String, Object> payload = toLicensePayload(remote, true);
    payload.put("trialClaimed", true);
    if (!email.isEmpty() && str(payload.get("onlineEmail")).isEmpty()) {
      payload.put("onlineEmail", email);
    }
    return licenses.applyOnline(payload);
  }

  public Map<String, Object> refresh() {
    Map<String, Object> stored = licenses.storedRaw();
    boolean online =
        Boolean.TRUE.equals(stored.get("online"))
            || "true".equalsIgnoreCase(String.valueOf(stored.getOrDefault("online", "")));
    if (!online) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_NOT_LOGGED);
    }
    String sn = MachineFingerprint.machineSn(props.getMachineSn());
    List<String> machineMacAddresses = MachineFingerprint.macAddresses();
    if (isLocal()) {
      Map<String, Object> payload = new LinkedHashMap<>(stored);
      payload.put("sn", sn);
      payload.put("macAddresses", machineMacAddresses);
      payload.put("online", true);
      return licenses.applyOnline(payload);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sn", sn);
    body.put("macAddresses", machineMacAddresses);
    body.put("onlineEmail", str(stored.get("onlineEmail")));
    body.put("license", str(stored.get("license")));
    Map<String, Object> remote = onlineClient.post(requireRemoteUrl(), PATH_REFRESH, body);
    return licenses.applyOnline(toLicensePayload(remote, true));
  }

  public Map<String, Object> logout() {
    if (!isLocal()) {
      Map<String, Object> stored = licenses.storedRaw();
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("sn", MachineFingerprint.machineSn(props.getMachineSn()));
      body.put("onlineEmail", str(stored.get("onlineEmail")));
      body.put("license", str(stored.get("license")));
      try {
        onlineClient.post(requireRemoteUrl(), PATH_LOGOUT, body);
      } catch (BusinessException e) {
        // 商店不可达时仍允许本机退出，避免卡死在线态
        log.warn("license online remote logout failed: {}", e.getMessageKey());
      }
    }
    Map<String, Object> fallback = new LinkedHashMap<>();
    fallback.put("license", "");
    fallback.put("people", 3);
    fallback.put("sn", "");
    fallback.put("macAddresses", List.of());
    fallback.put("expiredAt", "");
    fallback.put("online", false);
    fallback.put("onlineEmail", "");
    return licenses.applyOnline(fallback);
  }

  private String requireRemoteUrl() {
    String url = props.getLicenseOnlineUrl();
    if (url == null || url.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_ONLINE_UNAVAILABLE);
    }
    return url.trim();
  }

  private boolean isLocal() {
    String mode = props.getLicenseOnlineMode();
    return mode == null || mode.isBlank() || "local".equalsIgnoreCase(mode.trim());
  }

  /** 将商店响应规范为落盘 License 字段。 */
  static Map<String, Object> toLicensePayload(Map<String, Object> remote, boolean forceOnline) {
    Map<String, Object> src = unwrapData(remote);
    Object nested = src.get("license");
    if (nested instanceof Map<?, ?> m && !src.containsKey("people")) {
      Map<String, Object> copy = new LinkedHashMap<>();
      m.forEach((k, v) -> copy.put(String.valueOf(k), v));
      src = copy;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    Object licenseVal = first(src, "license");
    if (licenseVal instanceof Map<?, ?>) {
      payload.put("license", "");
    } else {
      payload.put("license", str(licenseVal));
    }
    payload.put("people", asInt(first(src, "people"), 0));
    payload.put("sn", str(first(src, "sn")));
    Object mac = first(src, "macAddresses", "macs");
    payload.put("macAddresses", asStringList(mac));
    Object exp = first(src, "expiredAt", "expired_at");
    payload.put("expiredAt", exp == null ? "" : String.valueOf(exp).trim());
    if (forceOnline) {
      payload.put("online", true);
    } else {
      Object online = first(src, "online");
      payload.put(
          "online",
          Boolean.TRUE.equals(online) || "true".equalsIgnoreCase(String.valueOf(online)));
    }
    payload.put("onlineEmail", str(first(src, "onlineEmail", "online_email", "email")));
    return payload;
  }

  private static Map<String, Object> unwrapData(Map<String, Object> remote) {
    if (remote == null) {
      return Map.of();
    }
    Object data = remote.get("data");
    if (data instanceof Map<?, ?> m) {
      Map<String, Object> copy = new LinkedHashMap<>();
      m.forEach((k, v) -> copy.put(String.valueOf(k), v));
      return copy;
    }
    return remote;
  }

  private static Object first(Map<String, Object> map, String... keys) {
    for (String k : keys) {
      if (map.containsKey(k) && map.get(k) != null) {
        return map.get(k);
      }
    }
    return null;
  }

  private static List<String> asStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object o : list) {
        if (o != null && !String.valueOf(o).isBlank()) {
          out.add(String.valueOf(o).trim());
        }
      }
      return out;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String p : s.split("[,;\\s]+")) {
      if (!p.isBlank()) {
        out.add(p.trim());
      }
    }
    return out;
  }

  private static int asInt(Object v, int def) {
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static long asLong(Object v, long def) {
    if (v == null) {
      return def;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String normalizeEmail(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_EMAIL_INVALID);
    }
    String email = raw.trim().toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(email).matches()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_EMAIL_INVALID);
    }
    return email;
  }
}
