package com.bluedock.system.oss;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.OssProvider;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/** OSS 设置规范化：默认值、protocol/domain ↔ publicBaseUrl、allowExtensions。 */
public final class OssSettingsSupport {

  private static final Pattern EXT_TOKEN = Pattern.compile("^[a-z0-9]{1,16}$");
  private static final Set<String> NAME_TYPES = Set.of(OssSettingsDocument.NAME_HASH,
      OssSettingsDocument.NAME_DATE_RANDOM);
  private static final Set<String> LINK_TYPES = Set.of(
      OssSettingsDocument.LINK_SIMPLE,
      OssSettingsDocument.LINK_FULL,
      OssSettingsDocument.LINK_SIMPLE_COMPRESS,
      OssSettingsDocument.LINK_FULL_COMPRESS);
  private static final Set<String> PROTOCOLS = Set.of(
      OssSettingsDocument.PROTO_FOLLOW,
      OssSettingsDocument.PROTO_HTTP,
      OssSettingsDocument.PROTO_HTTPS,
      OssSettingsDocument.PROTO_PATH,
      OssSettingsDocument.PROTO_AUTO);

  private OssSettingsSupport() {
  }

  /**
   * 填充缺省、拆分/合成 URL、规范化 allowExtensions；云厂商非法 protocol 回退 https。
   *
   * @param forPersist true 时云无可用 publicBaseUrl 则抛错
   */
  public static OssSettingsDocument normalize(OssSettingsDocument raw, boolean forPersist) {
    if (raw == null || !StringUtils.hasText(raw.provider())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_PROVIDER_REQUIRED);
    }
    String provider = OssProvider.fromConfig(raw.provider()).name().toLowerCase(Locale.ROOT);
    String nameType = pickEnum(raw.nameType(), NAME_TYPES, OssSettingsDocument.NAME_DATE_RANDOM);
    String linkType = pickEnum(raw.linkType(), LINK_TYPES, OssSettingsDocument.LINK_SIMPLE);
    String allowExtensions = normalizeAllowExtensions(raw.allowExtensions());
    String protocol = pickEnum(raw.protocol(), PROTOCOLS, OssSettingsDocument.PROTO_HTTPS);
    String domain = trimDomain(raw.domain());
    String publicBaseUrl = trimBase(raw.publicBaseUrl());

    // GET 兼容旧数据：从 publicBaseUrl 反拆 protocol/domain。
    // PUT 时云厂商不得用旧 publicBaseUrl「补」空 domain（否则切云会误用 localhost）。
    if (!StringUtils.hasText(domain) && StringUtils.hasText(publicBaseUrl) && !forPersist) {
      Split split = splitPublicBaseUrl(publicBaseUrl);
      if (split != null) {
        protocol = split.protocol();
        domain = split.domain();
      }
    }

    if (!"local".equals(provider)
        && (OssSettingsDocument.PROTO_FOLLOW.equals(protocol)
            || OssSettingsDocument.PROTO_PATH.equals(protocol))) {
      protocol = OssSettingsDocument.PROTO_HTTPS;
    }

    if (forPersist
        && !"local".equals(provider)
        && !StringUtils.hasText(domain)
        && !OssSettingsDocument.PROTO_PATH.equals(protocol)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DOMAIN_REQUIRED);
    }

    publicBaseUrl = composePublicBaseUrl(provider, protocol, domain, publicBaseUrl);

    if (forPersist
        && !"local".equals(provider)
        && !StringUtils.hasText(publicBaseUrl)
        && !OssSettingsDocument.PROTO_PATH.equals(protocol)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DOMAIN_REQUIRED);
    }

    OssSettingsDocument.Local local = raw.local() != null
        ? new OssSettingsDocument.Local(
            StringUtils.hasText(raw.local().storagePath())
                ? raw.local().storagePath().trim()
                : "./data/uploads")
        : new OssSettingsDocument.Local("./data/uploads");

    return new OssSettingsDocument(
        provider,
        nameType,
        linkType,
        allowExtensions,
        protocol,
        domain,
        publicBaseUrl,
        local,
        raw.huawei(),
        raw.aliyun(),
        raw.tencent(),
        raw.qiniu());
  }

  public static String normalizeAllowExtensions(String raw) {
    String source = StringUtils.hasText(raw) ? raw : OssSettingsDocument.DEFAULT_ALLOW_EXTENSIONS;
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    for (String part : source.split("[,，;\\s]+")) {
      if (!StringUtils.hasText(part)) {
        continue;
      }
      String t = part.trim().toLowerCase(Locale.ROOT);
      if (t.startsWith(".")) {
        t = t.substring(1);
      }
      if (!EXT_TOKEN.matcher(t).matches()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ALLOW_EXTS_INVALID);
      }
      tokens.add(t);
    }
    if (tokens.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ALLOW_EXTS_INVALID);
    }
    return String.join(",", tokens);
  }

  public static void assertExtensionAllowed(String allowExtensions, String originalFilename) {
    String normalized = normalizeAllowExtensions(allowExtensions);
    Set<String> allowed = Arrays.stream(normalized.split(",")).collect(Collectors.toCollection(LinkedHashSet::new));
    String extension = extensionOf(originalFilename);
    if (!StringUtils.hasText(extension) || !allowed.contains(extension)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_EXT_NOT_ALLOWED);
    }
  }

  public static String extensionOf(String filename) {
    if (!StringUtils.hasText(filename)) {
      return "";
    }
    String name = filename.trim();
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  public static String composePublicBaseUrl(
      String provider, String protocol, String domain, String fallbackPublicBaseUrl) {
    String d = trimDomain(domain);
    return switch (protocol) {
      case OssSettingsDocument.PROTO_PATH -> "";
      case OssSettingsDocument.PROTO_HTTP -> requireDomainOrFallback(provider, d, "http", fallbackPublicBaseUrl);
      case OssSettingsDocument.PROTO_HTTPS, OssSettingsDocument.PROTO_AUTO ->
        requireDomainOrFallback(provider, d, "https", fallbackPublicBaseUrl);
      case OssSettingsDocument.PROTO_FOLLOW -> {
        if (StringUtils.hasText(d)) {
          yield "https://" + d;
        }
        yield trimBase(fallbackPublicBaseUrl);
      }
      default -> trimBase(fallbackPublicBaseUrl);
    };
  }

  private static String requireDomainOrFallback(
      String provider, String domain, String scheme, String fallback) {
    if (StringUtils.hasText(domain)) {
      return scheme + "://" + domain;
    }
    if ("local".equals(provider)) {
      return trimBase(fallback);
    }
    return "";
  }

  public static Split splitPublicBaseUrl(String publicBaseUrl) {
    String raw = trimBase(publicBaseUrl);
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    try {
      URI uri = URI.create(raw.contains("://") ? raw : "https://" + raw);
      String scheme = uri.getScheme();
      String protocol = "http".equalsIgnoreCase(scheme)
          ? OssSettingsDocument.PROTO_HTTP
          : OssSettingsDocument.PROTO_HTTPS;
      String host = uri.getHost();
      if (!StringUtils.hasText(host)) {
        // path-only or opaque
        String noScheme = raw.replaceFirst("(?i)^https?://", "");
        return new Split(protocol, trimDomain(noScheme));
      }
      StringBuilder domain = new StringBuilder(host);
      if (uri.getPort() > 0) {
        domain.append(':').append(uri.getPort());
      }
      if (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath())) {
        String path = uri.getPath();
        if (path.endsWith("/")) {
          path = path.substring(0, path.length() - 1);
        }
        domain.append(path);
      }
      return new Split(protocol, domain.toString());
    } catch (IllegalArgumentException ex) {
      return new Split(OssSettingsDocument.PROTO_HTTPS, trimDomain(raw.replaceFirst("(?i)^https?://", "")));
    }
  }

  private static String pickEnum(String value, Set<String> allowed, String defaultValue) {
    if (!StringUtils.hasText(value)) {
      return defaultValue;
    }
    String v = value.trim();
    return allowed.contains(v) ? v : defaultValue;
  }

  private static String trimDomain(String domain) {
    if (!StringUtils.hasText(domain)) {
      return "";
    }
    String d = domain.trim();
    d = d.replaceFirst("(?i)^https?://", "");
    while (d.endsWith("/")) {
      d = d.substring(0, d.length() - 1);
    }
    return d;
  }

  private static String trimBase(String base) {
    if (!StringUtils.hasText(base)) {
      return "";
    }
    String b = base.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return b;
  }

  public record Split(String protocol, String domain) {
  }
}
