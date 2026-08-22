package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.system.config.SystemProperties;
import com.bluedock.system.license.MachineFingerprint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 离线 License 读写与校验（人数 / 过期 / SN / MAC）。
 *
 * <p>管理员可粘贴结构化 JSON（含 {@code people}/{@code sn}/{@code mac}/{@code expiredAt}），或仅粘贴原文（按试用档
 * people=0 存储，不绑 SN/MAC）。在线授权 {@code api/license/*} 另开。
 */
@Service
public class LicenseService implements LicenseCapacity {
  private final SystemProperties props;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final JdbcTemplate jdbc;

  public LicenseService(
      SystemProperties props,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      JdbcTemplate jdbc) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.jdbc = jdbc;
  }

  public Map<String, Object> status() {
    Map<String, Object> stored = readFile();
    int people = asInt(stored.get("people"), 0);
    String expiredAt =
        stored.get("expiredAt") == null ? "" : String.valueOf(stored.get("expiredAt")).trim();
    String sn = str(stored.get("sn"));
    List<String> licenseMacAddresses = parseMacAddresses(stored.get("macAddresses"));
    int userCount = countActiveUsers();
    String machineSn = MachineFingerprint.machineSn(props.getMachineSn());
    List<String> machineMacAddresses = MachineFingerprint.macAddresses();

    List<String> errors = new ArrayList<>();
    if (requiresBinding(people)) {
      if (!sn.isEmpty() && !sn.equals(machineSn)) {
        errors.add(Messages.get(I18nKeys.LICENSE_SN_MISMATCH));
      }
      if (!licenseMacAddresses.isEmpty() && !machineMacAddresses.isEmpty()) {
        List<String> upper = machineMacAddresses.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
        boolean ok = false;
        for (String m : licenseMacAddresses) {
          if (upper.contains(m.toUpperCase(Locale.ROOT))) {
            ok = true;
            break;
          }
        }
        if (!ok) {
          errors.add(Messages.get(I18nKeys.LICENSE_MAC_MISMATCH));
        }
      }
    }
    if (people > 0 && userCount > people) {
      errors.add(Messages.get(I18nKeys.LICENSE_PEOPLE_EXCEEDED));
    }
    if (isExpired(expiredAt)) {
      errors.add(Messages.get(I18nKeys.LICENSE_EXPIRED));
    }

    Map<String, Object> info = new LinkedHashMap<>();
    info.put("people", people);
    info.put("sn", sn);
    info.put("macAddresses", licenseMacAddresses);
    info.put("expiredAt", expiredAt);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("license", stored.getOrDefault("license", ""));
    data.put("info", info);
    data.put("userCount", userCount);
    data.put("macAddresses", machineMacAddresses);
    data.put("machineSn", machineSn);
    data.put("error", errors);
    data.put("trial", people > 0 && people <= 3);
    data.put("ok", errors.isEmpty());
    data.put("online", Boolean.TRUE.equals(stored.get("online")) || "true".equalsIgnoreCase(str(stored.get("online"))));
    data.put("onlineEmail", str(stored.get("onlineEmail")));
    data.put("onlineMode", props.getLicenseOnlineMode() == null ? "local" : props.getLicenseOnlineMode());
    return data;
  }

  public Map<String, Object> save(String license) {
    adminGuard.requireAdmin();
    String content = license == null ? "" : license.trim();
    if (content.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_INVALID);
    }
    Map<String, Object> payload = parseIncoming(content);
    return persist(payload);
  }

  /**
   * 在线授权写入（无需管理员）；校验绑定后落盘。
   */
  public Map<String, Object> applyOnline(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_INVALID);
    }
    Map<String, Object> copy = new LinkedHashMap<>(payload);
    return persist(copy);
  }

  private Map<String, Object> persist(Map<String, Object> payload) {
    int people = asInt(payload.get("people"), 0);
    String sn = str(payload.get("sn"));
    if (requiresBinding(people) && sn.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_INVALID);
    }
    String bindingErr = bindingError(people, sn, parseMacAddresses(payload.get("macAddresses")));
    if (bindingErr != null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, bindingErr);
    }
    try {
      Path path = Path.of(props.getLicensePath()).toAbsolutePath().normalize();
      Files.createDirectories(path.getParent());
      Files.writeString(
          path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_INVALID);
    }
    return status();
  }

  /** 当前落盘原文（含 online 元数据）。 */
  public Map<String, Object> storedRaw() {
    return readFile();
  }

  /** 注册 / 拉人前调用：人数已满则拒绝。 */
  @Override
  public void assertCanAddUser() {
    Map<String, Object> stored = readFile();
    int people = asInt(stored.get("people"), 0);
    if (people <= 0) {
      return;
    }
    if (countActiveUsers() >= people) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.LICENSE_PEOPLE_EXCEEDED);
    }
    String expiredAt = str(stored.get("expiredAt"));
    if (isExpired(expiredAt)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.LICENSE_EXPIRED);
    }
  }

  private Map<String, Object> parseIncoming(String content) {
    Map<String, Object> decoded = tryParseJsonObject(content);
    if (decoded == null) {
      decoded = tryParseBase64Json(content);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    if (decoded != null) {
      payload.put(
          "license",
          decoded.containsKey("license") ? str(decoded.get("license")) : content);
      payload.put("people", asInt(first(decoded, "people"), 0));
      payload.put("sn", str(first(decoded, "sn")));
      Object macAddressesRaw = first(decoded, "macAddresses");
      if (macAddressesRaw == null) {
        // 外部 License 原文可能仍为 macs
        macAddressesRaw = first(decoded, "macs");
      }
      payload.put("macAddresses", parseMacAddresses(macAddressesRaw));
      Object exp = first(decoded, "expiredAt");
      if (exp == null) {
        // 外部 License 原文可能仍为 expired_at
        exp = first(decoded, "expired_at");
      }
      payload.put("expiredAt", normalizeExpired(exp));
      return payload;
    }
    // 纯原文：按试用档存储（people=0，不绑 SN/MAC）
    payload.put("license", content);
    payload.put("people", 0);
    payload.put("sn", "");
    payload.put("macAddresses", List.of());
    payload.put("expiredAt", "");
    return payload;
  }

  private String bindingError(int people, String sn, List<String> licenseMacAddresses) {
    if (!requiresBinding(people)) {
      return null;
    }
    String machineSn = MachineFingerprint.machineSn(props.getMachineSn());
    if (!sn.isEmpty() && !sn.equals(machineSn)) {
      return I18nKeys.LICENSE_SN_MISMATCH;
    }
    List<String> machineMacAddresses = MachineFingerprint.macAddresses();
    if (!licenseMacAddresses.isEmpty() && !machineMacAddresses.isEmpty()) {
      List<String> upper = machineMacAddresses.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
      boolean ok = false;
      for (String m : licenseMacAddresses) {
        if (upper.contains(m.toUpperCase(Locale.ROOT))) {
          ok = true;
          break;
        }
      }
      if (!ok) {
        return I18nKeys.LICENSE_MAC_MISMATCH;
      }
    }
    return null;
  }

  /** people==0 或 >3：付费/企业档，校验 SN/MAC；1–3：小团队豁免绑定。 */
  static boolean requiresBinding(int people) {
    return people == 0 || people > 3;
  }

  static boolean isExpired(String expiredAt) {
    if (expiredAt == null || expiredAt.isBlank() || "0".equals(expiredAt) || "forever".equalsIgnoreCase(expiredAt)) {
      return false;
    }
    LocalDateTime end = parseExpired(expiredAt);
    return end != null && !end.isAfter(LocalDateTime.now());
  }

  private static LocalDateTime parseExpired(String raw) {
    String s = raw.trim();
    DateTimeFormatter[] fmts =
        new DateTimeFormatter[] {
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
          DateTimeFormatter.ISO_LOCAL_DATE_TIME,
          DateTimeFormatter.ISO_OFFSET_DATE_TIME
        };
    for (DateTimeFormatter f : fmts) {
      try {
        return LocalDateTime.parse(s, f);
      } catch (DateTimeParseException ignored) {
        // next
      }
    }
    try {
      return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String normalizeExpired(Object exp) {
    if (exp == null) {
      return "";
    }
    String s = String.valueOf(exp).trim();
    if ("forever".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) {
      return "";
    }
    return s;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> tryParseJsonObject(String content) {
    String t = content.trim();
    if (!t.startsWith("{")) {
      return null;
    }
    try {
      Object v = objectMapper.readValue(t, Object.class);
      if (v instanceof Map<?, ?> m) {
        return new LinkedHashMap<>((Map<String, Object>) m);
      }
    } catch (Exception ignored) {
      // not json
    }
    return null;
  }

  private Map<String, Object> tryParseBase64Json(String content) {
    try {
      byte[] raw = Base64.getDecoder().decode(content.replaceAll("\\s", ""));
      String json = new String(raw, StandardCharsets.UTF_8).trim();
      return tryParseJsonObject(json);
    } catch (Exception e) {
      return null;
    }
  }

  private Map<String, Object> readFile() {
    try {
      Path path = Path.of(props.getLicensePath()).toAbsolutePath().normalize();
      if (!Files.exists(path)) {
        return emptyStored();
      }
      return objectMapper.readValue(Files.readString(path), new TypeReference<>() {});
    } catch (Exception e) {
      return emptyStored();
    }
  }

  private static Map<String, Object> emptyStored() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("license", "");
    m.put("people", 0);
    m.put("sn", "");
    m.put("macAddresses", List.of());
    m.put("expiredAt", "");
    return m;
  }

  private int countActiveUsers() {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_users
            WHERE is_bot = 0 AND disable_at IS NULL
            """,
            Integer.class);
    return n == null ? 0 : n;
  }

  static List<String> parseMacAddresses(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        if (o != null) {
          String m = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
          if (MachineFingerprint.isMacAddress(m)) {
            out.add(m);
          }
        }
      }
      return out;
    }
    for (String part : String.valueOf(raw).split("[,;\\s]+")) {
      String m = part.trim().toUpperCase(Locale.ROOT);
      if (MachineFingerprint.isMacAddress(m)) {
        out.add(m);
      }
    }
    return out;
  }

  private static Object first(Map<String, Object> m, String key) {
    if (m.containsKey(key)) {
      return m.get(key);
    }
    return null;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static int asInt(Object v, int def) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
