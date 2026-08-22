package com.bluedock.common.oss;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.util.StringUtils;

/**
 * 本地落盘：根目录默认空则 {@code ./data/uploads}，按 key 类型分子目录，例如 {@code
 * public/releases/...}、{@code public/media/...}。
 */
public final class LocalObjectStorage implements ObjectStorage {

  private final OssProperties properties;
  private final String fallbackPublicBaseUrl;

  public LocalObjectStorage(OssProperties properties, String fallbackPublicBaseUrl) {
    this.properties = properties;
    this.fallbackPublicBaseUrl =
        fallbackPublicBaseUrl == null ? "" : fallbackPublicBaseUrl;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    String normalized = normalizeKey(key);
    Path target = resolveRoot().resolve(normalized).normalize();
    if (!target.startsWith(resolveRoot())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ILLEGAL_PATH);
    }
    try {
      Files.createDirectories(target.getParent());
      Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_SAVE_OBJECT_FAILED);
    }
    return buildUrl(normalized);
  }

  @Override
  public void delete(String key) {
    String normalized = normalizeKey(key);
    Path target = resolveRoot().resolve(normalized).normalize();
    if (!target.startsWith(resolveRoot())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ILLEGAL_PATH);
    }
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DELETE_FAILED);
    }
  }

  @Override
  public InputStream open(String key) {
    String normalized = normalizeKey(key);
    Path target = resolveRoot().resolve(normalized).normalize();
    if (!target.startsWith(resolveRoot())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ILLEGAL_PATH);
    }
    if (!Files.isRegularFile(target)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
    try {
      return Files.newInputStream(target);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  @Override
  public String providerId() {
    return "local";
  }

  public Path resolveRoot() {
    String path = properties.getLocal().getStoragePath();
    if (!StringUtils.hasText(path)) {
      path = "./data/uploads";
    }
    return Path.of(path).toAbsolutePath().normalize();
  }

  private String buildUrl(String key) {
    String base = properties.getPublicBaseUrl();
    if (!StringUtils.hasText(base)) {
      base = fallbackPublicBaseUrl;
    }
    String trimmed = base == null ? "" : base.replaceAll("/+$", "");
    if (trimmed.isEmpty()) {
      return "/" + key;
    }
    return trimmed + "/" + key;
  }

  static String normalizeKey(String key) {
    if (!StringUtils.hasText(key)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_OBJECT_KEY_REQUIRED);
    }
    String rel = key.replace('\\', '/').replaceAll("^/+", "");
    if (rel.contains("..")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_ILLEGAL_PATH);
    }
    return rel;
  }
}
