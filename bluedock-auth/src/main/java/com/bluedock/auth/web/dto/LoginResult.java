package com.bluedock.auth.web.dto;

/** 登录成功：短效 {@code token}（access）+ 长效 {@code refreshToken} + 用户公开视图。 */
public record LoginResult(String token, String refreshToken, UserPublicView user) {}
