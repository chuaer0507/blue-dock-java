package com.bluedock.boot.web;

import com.bluedock.common.i18n.Messages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析请求语言：Accept-Language → zh-CN。前端应按用户 lang 设置 Accept-Language。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LocaleFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Locale locale = Messages.fromAcceptLanguage(request.getHeader("Accept-Language"));
    LocaleContextHolder.setLocale(locale);
    try {
      filterChain.doFilter(request, response);
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }
}
