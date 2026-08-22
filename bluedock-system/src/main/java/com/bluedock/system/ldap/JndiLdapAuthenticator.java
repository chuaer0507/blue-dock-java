package com.bluedock.system.ldap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.ldap.LdapAuthenticator;
import com.bluedock.auth.ldap.LdapUserInfo;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.LdapSettingService;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JndiLdapAuthenticator implements LdapAuthenticator {
  private static final Logger log = LoggerFactory.getLogger(JndiLdapAuthenticator.class);
  private static final List<String> EMAIL_ATTRS =
      List.of("mail", "cn", "uid", "userPrincipalName");

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;

  public JndiLdapAuthenticator(SettingRepository settings, ObjectMapper objectMapper) {
    this.settings = settings;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean isEnabled() {
    Map<String, Object> cfg = load();
    return open(str(cfg, "ldapOpen"));
  }

  @Override
  public Optional<LdapUserInfo> authenticate(String login, String password) {
    if (login == null || login.isBlank() || password == null || password.isBlank()) {
      return Optional.empty();
    }
    Map<String, Object> cfg = load();
    if (!open(str(cfg, "ldapOpen"))) {
      return Optional.empty();
    }
    String host = str(cfg, "ldapHost");
    String port = str(cfg, "ldapPort");
    String adminDn = str(cfg, "ldapUserDn");
    String adminPassword = str(cfg, "ldapPassword");
    String baseDn = str(cfg, "ldapBaseDn");
    String loginAttr = loginAttr(cfg);
    if (host.isBlank() || adminDn.isBlank() || baseDn.isBlank()) {
      return Optional.empty();
    }
    String url = urlOf(host, port);
    DirContext adminCtx = null;
    DirContext userCtx = null;
    try {
      adminCtx = bind(url, adminDn, adminPassword);
      String filter = "(" + loginAttr + "=" + escapeFilter(login.trim()) + ")";
      SearchControls sc = new SearchControls();
      sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
      sc.setReturningAttributes(new String[] {"mail", "cn", "displayName", "uid", loginAttr});
      NamingEnumeration<SearchResult> results = adminCtx.search(baseDn, filter, sc);
      if (!results.hasMore()) {
        return Optional.empty();
      }
      SearchResult sr = results.next();
      String userDn = sr.getNameInNamespace();
      userCtx = bind(url, userDn, password);

      Attributes attrs = sr.getAttributes();
      String mail = first(attrs, "mail");
      if (mail == null || mail.isBlank()) {
        if (login.contains("@")) {
          mail = login.trim();
        } else {
          return Optional.empty();
        }
      }
      String nick = first(attrs, "displayName");
      if (nick == null || nick.isBlank()) {
        nick = first(attrs, "cn");
      }
      if (nick == null || nick.isBlank()) {
        nick = first(attrs, loginAttr);
      }
      return Optional.of(
          new LdapUserInfo(mail.trim().toLowerCase(Locale.ROOT), nick == null ? "" : nick, userDn));
    } catch (Exception e) {
      log.debug("ldap auth failed login={}: {}", login, e.toString());
      return Optional.empty();
    } finally {
      closeQuietly(userCtx);
      closeQuietly(adminCtx);
    }
  }

  @Override
  public boolean syncLocalUser(String email, String nickname, String plainPassword) {
    if (email == null || email.isBlank() || plainPassword == null || plainPassword.isBlank()) {
      return false;
    }
    Map<String, Object> cfg = load();
    if (!open(str(cfg, "ldapOpen"))) {
      return false;
    }
    if (!open(str(cfg, "ldapSyncLocal"))) {
      return false;
    }
    String host = str(cfg, "ldapHost");
    String port = str(cfg, "ldapPort");
    String adminDn = str(cfg, "ldapUserDn");
    String adminPassword = str(cfg, "ldapPassword");
    String baseDn = str(cfg, "ldapBaseDn");
    if (host.isBlank() || adminDn.isBlank() || baseDn.isBlank()) {
      return false;
    }
    String mail = email.trim().toLowerCase(Locale.ROOT);
    String nick =
        nickname == null || nickname.isBlank() ? mail.split("@")[0] : nickname.trim();
    String url = urlOf(host, port);
    DirContext adminCtx = null;
    try {
      adminCtx = bind(url, adminDn, adminPassword);
      if (findDnByEmail(adminCtx, baseDn, mail).isPresent()) {
        return false;
      }
      String rdn = "cn=" + escapeDn(mail);
      String dn = rdn + "," + baseDn;
      Attributes attrs = new BasicAttributes(true);
      Attribute oc = new BasicAttribute("objectClass");
      oc.add("top");
      oc.add("person");
      oc.add("organizationalPerson");
      oc.add("inetOrgPerson");
      attrs.put(oc);
      attrs.put("cn", mail);
      attrs.put("sn", mail);
      attrs.put("uid", mail);
      attrs.put("mail", mail);
      attrs.put("displayName", nick);
      attrs.put("userPassword", plainPassword);
      adminCtx.createSubcontext(dn, attrs);
      log.info("ldap syncLocal created dn={}", dn);
      return true;
    } catch (NameAlreadyBoundException e) {
      log.debug("ldap syncLocal already exists email={}", mail);
      return false;
    } catch (Exception e) {
      log.warn("ldap syncLocal fail email={}: {}", mail, e.toString());
      return false;
    } finally {
      closeQuietly(adminCtx);
    }
  }

  @Override
  public boolean updatePassword(String email, String newPlainPassword) {
    if (email == null || email.isBlank() || newPlainPassword == null || newPlainPassword.isBlank()) {
      return false;
    }
    Map<String, Object> cfg = load();
    if (!open(str(cfg, "ldapOpen"))) {
      return false;
    }
    String host = str(cfg, "ldapHost");
    String port = str(cfg, "ldapPort");
    String adminDn = str(cfg, "ldapUserDn");
    String adminPassword = str(cfg, "ldapPassword");
    String baseDn = str(cfg, "ldapBaseDn");
    if (host.isBlank() || adminDn.isBlank() || baseDn.isBlank()) {
      return false;
    }
    String mail = email.trim().toLowerCase(Locale.ROOT);
    String url = urlOf(host, port);
    DirContext adminCtx = null;
    try {
      adminCtx = bind(url, adminDn, adminPassword);
      Optional<String> dn = findDnByEmail(adminCtx, baseDn, mail);
      if (dn.isEmpty()) {
        log.debug("ldap updatePassword no entry email={}", mail);
        return false;
      }
      Attributes attrs = new BasicAttributes();
      attrs.put("userPassword", newPlainPassword);
      adminCtx.modifyAttributes(dn.get(), DirContext.REPLACE_ATTRIBUTE, attrs);
      log.info("ldap updatePassword ok dn={}", dn.get());
      return true;
    } catch (Exception e) {
      log.warn("ldap updatePassword fail email={}: {}", mail, e.toString());
      return false;
    } finally {
      closeQuietly(adminCtx);
    }
  }

  private Optional<String> findDnByEmail(DirContext ctx, String baseDn, String email)
      throws Exception {
    SearchControls sc = new SearchControls();
    sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
    sc.setReturningAttributes(new String[] {"dn"});
    sc.setCountLimit(1);
    for (String attr : EMAIL_ATTRS) {
      String filter = "(" + attr + "=" + escapeFilter(email) + ")";
      NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, sc);
      if (results.hasMore()) {
        return Optional.of(results.next().getNameInNamespace());
      }
    }
    return Optional.empty();
  }

  private Map<String, Object> load() {
    return settings
        .findSettingJson(LdapSettingService.SETTING_NAME)
        .map(
            json -> {
              try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
              } catch (Exception e) {
                return Map.<String, Object>of();
              }
            })
        .orElse(Map.of());
  }

  private static String loginAttr(Map<String, Object> cfg) {
    String a = str(cfg, "ldapLoginAttr");
    if (a.isBlank()) {
      return "cn";
    }
    return switch (a) {
      case "cn", "uid", "mail", "sAMAccountName", "userPrincipalName" -> a;
      default -> "cn";
    };
  }

  private static boolean open(String v) {
    return "open".equalsIgnoreCase(v == null ? "" : v.trim());
  }

  private static String urlOf(String host, String port) {
    return "ldap://" + host + ":" + (port == null || port.isBlank() ? "389" : port);
  }

  private static DirContext bind(String url, String dn, String password) throws Exception {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, url);
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, dn);
    env.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
    return new InitialDirContext(env);
  }

  private static String first(Attributes attrs, String name) throws Exception {
    if (attrs == null || name == null) {
      return null;
    }
    Attribute a = attrs.get(name);
    if (a == null || a.size() == 0) {
      return null;
    }
    Object v = a.get();
    return v == null ? null : String.valueOf(v);
  }

  private static String escapeFilter(String raw) {
    StringBuilder sb = new StringBuilder();
    for (char c : raw.toCharArray()) {
      switch (c) {
        case '\\' -> sb.append("\\5c");
        case '*' -> sb.append("\\2a");
        case '(' -> sb.append("\\28");
        case ')' -> sb.append("\\29");
        case '\0' -> sb.append("\\00");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  /** RFC 4514 简易 DN 转义（用于 cn=email 形式）。 */
  private static String escapeDn(String raw) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      boolean first = i == 0;
      boolean last = i == raw.length() - 1;
      if (c == '\\'
          || c == ','
          || c == '+'
          || c == '"'
          || c == '<'
          || c == '>'
          || c == ';'
          || c == '=') {
        sb.append('\\').append(c);
      } else if ((c == ' ' || c == '#') && first) {
        sb.append('\\').append(c);
      } else if (c == ' ' && last) {
        sb.append('\\').append(c);
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static void closeQuietly(DirContext ctx) {
    if (ctx != null) {
      try {
        ctx.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  private static String str(Map<String, Object> cfg, String key) {
    Object v = cfg.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }
}
