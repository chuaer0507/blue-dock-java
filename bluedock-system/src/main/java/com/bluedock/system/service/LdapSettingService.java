package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Service;

@Service
public class LdapSettingService {
  public static final String SETTING_NAME = "thirdAccessSetting";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public LdapSettingService(
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
    return load();
  }

  public Map<String, Object> save(Map<String, Object> body) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    Map<String, Object> current = load();
    if (body != null) {
      current.putAll(body);
    }
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(current));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LDAP_CONFIG);
    }
    return current;
  }

  public Map<String, Object> test() {
    adminGuard.requireAdmin();
    Map<String, Object> cfg = load();
    if (!"open".equals(String.valueOf(cfg.getOrDefault("ldapOpen", "")))) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LDAP_DISABLED);
    }
    String host = str(cfg, "ldapHost");
    String port = str(cfg, "ldapPort");
    String userDn = str(cfg, "ldapUserDn");
    String password = str(cfg, "ldapPassword");
    if (host.isBlank() || userDn.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LDAP_CONFIG);
    }
    String url = "ldap://" + host + ":" + (port.isBlank() ? "389" : port);
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, url);
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, userDn);
    env.put(Context.SECURITY_CREDENTIALS, password);
    try {
      new InitialDirContext(env).close();
      return Map.of("ok", true, "url", url);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LDAP_TEST_FAILED);
    }
  }

  private Map<String, Object> load() {
    return settings
        .findSettingJson(SETTING_NAME)
        .map(
            json -> {
              try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
              } catch (Exception e) {
                return defaults();
              }
            })
        .orElseGet(this::defaults);
  }

  private Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ldapOpen", "close");
    m.put("ldapHost", "");
    m.put("ldapPort", "389");
    m.put("ldapUserDn", "");
    m.put("ldapPassword", "");
    m.put("ldapBaseDn", "");
    m.put("ldapLoginAttr", "cn");
    m.put("ldapSyncLocal", "close");
    return m;
  }

  private static String str(Map<String, Object> cfg, String key) {
    Object v = cfg.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }
}
