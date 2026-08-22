package com.bluedock.common.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.springframework.context.i18n.LocaleContextHolder;

/** 解析 API message；默认 zh-CN，支持 en-US。 */
public final class Messages {
  private static final String BUNDLE = "i18n.messages";
  /** 对应 resources/i18n/messages_zh_CN.properties */
  private static final Locale DEFAULT = Locale.forLanguageTag("zh-CN");
  /** 对应 resources/i18n/messages_en_US.properties */
  private static final Locale ENGLISH = Locale.forLanguageTag("en-US");

  private Messages() {}

  public static String get(String key, Object... args) {
    return get(LocaleContextHolder.getLocale(), key, args);
  }

  public static String get(Locale locale, String key, Object... args) {
    Locale use = normalize(locale);
    String pattern;
    try {
      // 禁止回落到 JVM 默认 Locale（否则 en 机器上 zh-CN 会误用 messages_en_US）
      ResourceBundle bundle =
          ResourceBundle.getBundle(
              BUNDLE, use, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT));
      pattern = bundle.containsKey(key) ? bundle.getString(key) : key;
    } catch (MissingResourceException ex) {
      pattern = key;
    }
    if (args == null || args.length == 0) {
      return pattern;
    }
    return MessageFormat.format(pattern, args);
  }

  /** 仅支持 zh-CN / en-US 两套；其它回落中文。 */
  public static Locale normalize(Locale locale) {
    if (locale == null) {
      return DEFAULT;
    }
    if ("en".equalsIgnoreCase(locale.getLanguage())) {
      return ENGLISH;
    }
    return DEFAULT;
  }

  public static Locale normalize(String tag) {
    if (tag == null || tag.isBlank()) {
      return DEFAULT;
    }
    String t = tag.trim().replace('_', '-');
    if (t.toLowerCase(Locale.ROOT).startsWith("en")) {
      return ENGLISH;
    }
    return DEFAULT;
  }

  public static Locale fromAcceptLanguage(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return DEFAULT;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
      Locale match = Locale.lookup(ranges, List.of(ENGLISH, DEFAULT));
      return normalize(match);
    } catch (IllegalArgumentException ex) {
      return DEFAULT;
    }
  }

  /** 写入用户资料的标准值：仅 zh-CN / en-US。 */
  public static String toUserLang(String raw) {
    if (raw == null || raw.isBlank()) {
      return "zh-CN";
    }
    String t = raw.trim().replace('_', '-');
    if ("en-US".equalsIgnoreCase(t)) {
      return "en-US";
    }
    if ("zh-CN".equalsIgnoreCase(t)) {
      return "zh-CN";
    }
    return "zh-CN";
  }

  public static boolean isSupportedUserLang(String raw) {
    if (raw == null || raw.isBlank()) {
      return true;
    }
    String t = raw.trim().replace('_', '-');
    return "zh-CN".equalsIgnoreCase(t) || "en-US".equalsIgnoreCase(t);
  }
}
