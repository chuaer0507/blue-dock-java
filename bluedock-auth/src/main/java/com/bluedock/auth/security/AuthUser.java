package com.bluedock.auth.security;

/** 请求内当前登录用户；由 {@link BearerAuthFilter} 注入。 */
public record AuthUser(long userId) {}
