package com.bluedock.auth.web.dto;

/** POST `/api/users/login` JSON body（camelCase）。 */
public record LoginRequest(
    String email,
    String password,
    String keyId,
    String captchaKey,
    String captchaCode,
    String codeKey,
    String code) {}
