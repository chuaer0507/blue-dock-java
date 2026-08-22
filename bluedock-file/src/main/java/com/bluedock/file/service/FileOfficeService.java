package com.bluedock.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.config.OfficeProperties;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileContent;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.office.OfficeJwt;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FileContentView;
import com.bluedock.file.web.dto.OfficeTokenView;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileOfficeService {
  private static final Set<String> OFFICE_TYPES = Set.of("word", "excel", "ppt");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final FileRepository files;
  private final FileContentRepository contents;
  private final FileAccessService access;
  private final ChunkStorage storage;
  private final UploadProperties uploadProps;
  private final OfficeProperties props;
  private final StringRedisTemplate redis;

  public FileOfficeService(
      FileRepository files,
      FileContentRepository contents,
      FileAccessService access,
      ChunkStorage storage,
      UploadProperties uploadProps,
      OfficeProperties props,
      StringRedisTemplate redis) {
    this.files = files;
    this.contents = contents;
    this.access = access;
    this.storage = storage;
    this.uploadProps = uploadProps;
    this.props = props;
    this.redis = redis;
  }

  public OfficeTokenView token(long id, String mode) {
    ensureEnabled();
    long userId = AuthContext.requireUserId();
    String m = mode == null || mode.isBlank() ? "edit" : mode.trim().toLowerCase(Locale.ROOT);
    if (!"edit".equals(m) && !"view".equals(m)) {
      m = "edit";
    }
    FileEntry file =
        "edit".equals(m) ? access.requireWritable(id, userId) : access.requireReadable(id, userId);
    if (!OFFICE_TYPES.contains(file.getType() == null ? "" : file.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_TYPE_INVALID);
    }

    String token = UUID.randomUUID().toString().replace("-", "");
    String documentKey =
        file.getId()
            + "-"
            + (file.getUpdatedAt() == null ? "0" : Integer.toHexString(file.getUpdatedAt().hashCode()));
    String base = trimSlash(props.getPublicBaseUrl());
    String documentUrl = base + "/api/file/content/office?action=download&token=" + token;
    String callbackUrl = base + "/api/file/content/office?token=" + token;
    long ttl = Math.max(60L, props.getTokenTtlSeconds());

    try {
      String meta =
          JSON.writeValueAsString(
              Map.of(
                  "fileId", file.getId(),
                  "userId", userId,
                  "mode", m,
                  "documentKey", documentKey));
      redis.opsForValue().set(RedisKeys.officeToken(token), meta, Duration.ofSeconds(ttl));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_OFFICE_DISABLED);
    }

    String fileType = fileType(file);
    String documentType = documentType(file.getType());
    String jwtPayload =
        "{\"document\":{\"key\":\""
            + documentKey
            + "\",\"url\":\""
            + documentUrl
            + "\",\"fileType\":\""
            + fileType
            + "\",\"title\":\""
            + escapeJson(file.getName())
            + "\"},\"editorConfig\":{\"mode\":\""
            + m
            + "\",\"callbackUrl\":\""
            + callbackUrl
            + "\"}}";
    String jwt = OfficeJwt.sign(props.getJwtSecret(), jwtPayload);

    return new OfficeTokenView(
        token,
        documentKey,
        m,
        fileType,
        documentType,
        documentUrl,
        callbackUrl,
        props.getDocumentServerUrl() == null ? "" : props.getDocumentServerUrl(),
        file.getName() == null ? "" : file.getName(),
        jwt,
        ttl);
  }

  public Path resolveDownload(String token) {
    Map<String, Object> meta = requireToken(token);
    long fileId = ((Number) meta.get("fileId")).longValue();
    FileEntry file =
        files
            .findActive(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    String rel = file.getPath() == null ? "" : file.getPath().trim();
    if (rel.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
    try {
      Path abs = storage.resolveRelative(rel);
      if (!Files.isRegularFile(abs)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
      }
      return abs;
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
  }

  public String downloadFilename(String token) {
    Map<String, Object> meta = requireToken(token);
    long fileId = ((Number) meta.get("fileId")).longValue();
    return files.findActive(fileId).map(FileEntry::getName).orElse("document");
  }

  /**
   * OnlyOffice 回调：{@code status=2/6} 且带 {@code url} 时下载并落库；其它 status 返回空 ack。
   */
  @Transactional
  public FileContentView saveFromOffice(String token, Integer status, String url) {
    Map<String, Object> meta = requireToken(token);
    if (status != null && status != 2 && status != 6) {
      return emptyAck(
          ((Number) meta.get("fileId")).longValue(), ((Number) meta.get("userId")).longValue());
    }
    if ("view".equals(String.valueOf(meta.get("mode")))) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    if (url == null || url.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
    long fileId = ((Number) meta.get("fileId")).longValue();
    long userId = ((Number) meta.get("userId")).longValue();
    FileEntry file =
        files
            .findActive(fileId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    return saveBinaryFromUrl(file, userId, url.trim());
  }

  /** 登录用户主动用远端 URL 回写。 */
  @Transactional
  public FileContentView saveFromUrl(long id, String url) {
    long userId = AuthContext.requireUserId();
    FileEntry file = access.requireWritable(id, userId);
    if (!OFFICE_TYPES.contains(file.getType() == null ? "" : file.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_TYPE_INVALID);
    }
    if (url == null || url.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
    return saveBinaryFromUrl(file, userId, url.trim());
  }

  private FileContentView saveBinaryFromUrl(FileEntry file, long userId, String url) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
      HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_CONTENT_NOT_FOUND);
      }
      String type = file.getType() == null ? "file" : file.getType();
      Path absTarget = writeOfficeBytes(file.getId(), type, resp.body());
      long size = Files.size(absTarget);
      String rel = storage.relativePath(absTarget);
      LocalDateTime now = LocalDateTime.now();
      files.updateStorage(file.getId(), rel, "", size, now);
      String metaJson =
          JSON.writeValueAsString(
              Map.of("url", rel, "type", type, "extension", file.getExtension() == null ? "" : file.getExtension()));
      return insertVersion(file.getId(), userId, metaJson, size);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
  }

  private Path writeOfficeBytes(long fileId, String type, InputStream in) throws Exception {
    String yearMonth = LocalDate.now().format(YM);
    String safeType = (type == null || type.isBlank()) ? "file" : type;
    Path dir = Path.of(uploadProps.getBaseDir(), "file", safeType, yearMonth, String.valueOf(fileId));
    Files.createDirectories(dir);
    Path target = dir.resolve("content");
    try (InputStream body = in) {
      Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  private FileContentView insertVersion(long fileId, long userId, String body, long size) {
    LocalDateTime now = LocalDateTime.now();
    FileContent row = new FileContent();
    row.setId(IdGenerator.nextId());
    row.setFileId(fileId);
    row.setContent(body);
    row.setText("");
    row.setSize(size);
    row.setUserId(userId);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    contents.insert(row);
    return FileContentView.from(row);
  }

  private void ensureEnabled() {
    if (props.isEnabled() || props.isAllowDevToken()) {
      return;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_OFFICE_DISABLED);
  }

  private Map<String, Object> requireToken(String token) {
    String t = token == null ? "" : token.trim();
    if (t.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_OFFICE_TOKEN_INVALID);
    }
    String raw = redis.opsForValue().get(RedisKeys.officeToken(t));
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_OFFICE_TOKEN_INVALID);
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> meta = JSON.readValue(raw, Map.class);
      return meta;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_OFFICE_TOKEN_INVALID);
    }
  }

  private static FileContentView emptyAck(long fileId, long userId) {
    return new FileContentView(0L, fileId, "", "", 0L, userId, null);
  }

  private static String trimSlash(String base) {
    if (base == null || base.isBlank()) {
      return "http://127.0.0.1:18080";
    }
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static String fileType(FileEntry file) {
    String extension = file.getExtension() == null ? "" : file.getExtension().toLowerCase(Locale.ROOT);
    if (!extension.isEmpty()) {
      return extension;
    }
    return switch (file.getType() == null ? "" : file.getType()) {
      case "word" -> "docx";
      case "excel" -> "xlsx";
      case "ppt" -> "pptx";
      default -> "docx";
    };
  }

  private static String documentType(String type) {
    return switch (type == null ? "" : type) {
      case "excel" -> "cell";
      case "ppt" -> "slide";
      default -> "word";
    };
  }

  private static String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
