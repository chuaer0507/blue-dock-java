package com.bluedock.auth.web.dto;

/** 无感续期结果：新的 access + refresh（轮换）。 */
public record RefreshResult(String token, String refreshToken) {}
