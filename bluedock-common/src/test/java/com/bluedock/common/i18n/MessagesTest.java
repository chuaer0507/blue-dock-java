package com.bluedock.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class MessagesTest {
  private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");
  private static final Locale EN_US = Locale.forLanguageTag("en-US");

  @AfterEach
  void reset() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void zhDefault() {
    LocaleContextHolder.setLocale(ZH_CN);
    assertEquals("未登录", Messages.get(I18nKeys.UNAUTHORIZED));
  }

  @Test
  void en() {
    LocaleContextHolder.setLocale(EN_US);
    assertEquals("Not signed in", Messages.get(I18nKeys.UNAUTHORIZED));
  }

  @Test
  void args() {
    LocaleContextHolder.setLocale(EN_US);
    assertEquals("User not found: 9", Messages.get(I18nKeys.USER_NOT_FOUND_ID, 9L));
  }

  @Test
  void acceptLanguage() {
    assertEquals(EN_US, Messages.fromAcceptLanguage("en-US,en;q=0.9,zh-CN;q=0.8"));
    assertEquals(ZH_CN, Messages.fromAcceptLanguage("zh-CN,zh;q=0.9"));
  }

  @Test
  void userLang() {
    assertEquals("en-US", Messages.toUserLang("en-US"));
    assertEquals("zh-CN", Messages.toUserLang("zh-CN"));
    assertEquals("zh-CN", Messages.toUserLang("en"));
    assertEquals("zh-CN", Messages.toUserLang("zh"));
    assertTrue(Messages.isSupportedUserLang("en-US"));
    assertTrue(Messages.isSupportedUserLang("zh-CN"));
    assertTrue(!Messages.isSupportedUserLang("en"));
    assertTrue(!Messages.isSupportedUserLang("zh"));
  }
}
