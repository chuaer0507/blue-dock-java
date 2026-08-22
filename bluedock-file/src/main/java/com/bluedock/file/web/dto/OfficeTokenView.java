package com.bluedock.file.web.dto;

public record OfficeTokenView(
    String token,
    String documentKey,
    String mode,
    String fileType,
    String documentType,
    String documentUrl,
    String callbackUrl,
    String documentServerUrl,
    String filename,
    String jwt,
    long expiresIn) {}
