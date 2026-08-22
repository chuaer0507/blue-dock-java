package com.bluedock.system.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.oss.OssSettingsSupport;
import com.bluedock.system.upload.UploadObject;
import com.bluedock.system.upload.UploadObjectRepository;
import com.bluedock.system.upload.UploadObjectView;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统上传库：登记 {@code bluedock_upload_objects}；供 {@code imageUpload}/{@code fileUpload}/{@code
 * uploads} 共用。
 */
@Service
public class UploadObjectService {
  private static final Logger log = LoggerFactory.getLogger(UploadObjectService.class);
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final Set<String> IMAGE_EXT =
      Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

  public static final String CATEGORY_MEDIA = "media";
  public static final String CATEGORY_FILES = "files";
  public static final String CATEGORY_OTHER = "other";

  private final ObjectStorage objectStorage;
  private final OssSettingService oss;
  private final FileSettingService fileSetting;
  private final UploadObjectRepository objects;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public UploadObjectService(
      ObjectStorage objectStorage,
      OssSettingService oss,
      FileSettingService fileSetting,
      UploadObjectRepository objects,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.objectStorage = objectStorage;
    this.oss = oss;
    this.fileSetting = fileSetting;
    this.objects = objects;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  /** 登录用户直传图片（写库 category=media）。 */
  public Map<String, Object> imageUpload(MultipartFile file) {
    UploadObjectView view = store(file, CATEGORY_MEDIA, true, AuthContext.requireUserId());
    return toLegacyPayload(view);
  }

  /** 登录用户直传文件（写库 category=files）。 */
  public Map<String, Object> fileUpload(MultipartFile file) {
    UploadObjectView view = store(file, CATEGORY_FILES, false, AuthContext.requireUserId());
    return toLegacyPayload(view);
  }

  /** 管理员上传库入口。 */
  public UploadObjectView adminUpload(MultipartFile file, String category) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    String cat = normalizeCategory(category);
    boolean imageOnly = CATEGORY_MEDIA.equals(cat);
    return store(file, cat, imageOnly, AuthContext.requireUserId());
  }

  public Map<String, Object> list(String category, String q, Integer page, Integer pageSize) {
    adminGuard.requireAdmin();
    String cat = category == null || category.isBlank() ? null : normalizeCategory(category);
    String query = q == null ? "" : q.trim();
    if (query.length() > 64) {
      query = query.substring(0, 64);
    }
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
    long total = objects.count(cat, query.isEmpty() ? null : query);
    List<UploadObjectView> list =
        objects.page(cat, query.isEmpty() ? null : query, (p - 1) * size, size).stream()
            .map(UploadObjectView::from)
            .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("page", p);
    out.put("pageSize", size);
    out.put("total", total);
    return out;
  }

  /**
   * 本人图片空间（wire：{@code {dirs, files}}）。
   *
   * <p>数据源为 {@code bluedock_upload_objects}（{@code category=media}、当前用户），非本地目录。{@code
   * path} 可选，作 {@code object_key} 前缀过滤（如 {@code media/202608}）；无真实子目录时 {@code dirs}
   * 恒为空数组。
   */
  public Map<String, Object> imageView(String path) {
    long userId = AuthContext.requireUserId();
    String prefix = sanitizeImageViewPath(path);
    int limit = 200;
    List<UploadObject> rows =
        objects.pageByUploader(userId, CATEGORY_MEDIA, prefix.isEmpty() ? null : prefix, 0, limit);
    List<Map<String, Object>> files = new ArrayList<>(rows.size());
    for (UploadObject row : rows) {
      String url = row.getUrl() == null ? "" : row.getUrl();
      String key = row.getObjectKey() == null ? "" : row.getObjectKey();
      String title =
          row.getOriginalName() == null || row.getOriginalName().isBlank()
              ? key
              : row.getOriginalName();
      long inode = 0L;
      if (row.getCreatedAt() != null) {
        inode = row.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("type", "file");
      item.put("title", title);
      item.put("path", key);
      item.put("url", url);
      item.put("thumbnail", url);
      item.put("inode", inode);
      item.put("id", row.getId());
      files.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dirs", List.of());
    out.put("files", files);
    return out;
  }

  /** 去掉穿越与非法分隔，仅保留相对前缀。 */
  static String sanitizeImageViewPath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    String p = path.replace('|', '/').replace("..", "").trim();
    while (p.contains("//")) {
      p = p.replace("//", "/");
    }
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    while (p.endsWith("/")) {
      p = p.substring(0, p.length() - 1);
    }
    if (p.length() > 128) {
      p = p.substring(0, 128);
    }
    return p;
  }

  @Transactional
  public Map<String, Object> delete(long id) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    UploadObject row =
        objects
            .findActive(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.SYSTEM_UPLOAD_NOT_FOUND));
    objects.softDelete(id);
    try {
      objectStorage.delete(row.getObjectKey());
    } catch (Exception e) {
      log.warn("upload object storage delete failed id={} key={}: {}", id, row.getObjectKey(), e.toString());
    }
    return Map.of("ok", true);
  }

  private UploadObjectView store(
      MultipartFile file, String category, boolean imageOnly, long uploaderId) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_UPLOAD_EMPTY);
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    if (name.isEmpty()) {
      name = "upload.bin";
    }
    oss.assertAllowed(name);
    String extension = OssSettingsSupport.extensionOf(name);
    if (imageOnly && !IMAGE_EXT.contains(extension)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_UPLOAD_IMAGE_ONLY);
    }
    long size = file.getSize();
    long maxBytes = fileSetting.uploadMaxBytes();
    if (size <= 0 || size > maxBytes) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TOO_LARGE);
    }
    String prefix = CATEGORY_MEDIA.equals(category) ? "media" : "files";
    String key =
        prefix
            + "/"
            + LocalDate.now().format(YM)
            + "/"
            + IdGenerator.nextId()
            + (extension.isEmpty() ? "" : "." + extension);
    String contentType = file.getContentType() == null ? "" : file.getContentType();
    String url;
    try (InputStream in = file.getInputStream()) {
      url = objectStorage.put(key, in, size, contentType);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
    }
    UploadObject row = new UploadObject();
    row.setId(IdGenerator.nextId());
    row.setObjectKey(key);
    row.setUrl(url == null ? "" : url);
    row.setCategory(category);
    row.setOriginalName(name);
    row.setContentType(contentType);
    row.setSizeBytes(size);
    row.setProvider(oss.currentProviderId());
    row.setUploaderId(uploaderId);
    row.setCreatedAt(LocalDateTime.now());
    try {
      objects.insert(row);
    } catch (RuntimeException e) {
      try {
        objectStorage.delete(key);
      } catch (Exception ignored) {
        // best-effort
      }
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
    }
    return UploadObjectView.from(row);
  }

  private static Map<String, Object> toLegacyPayload(UploadObjectView view) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", view.id());
    out.put("url", view.url());
    out.put("path", view.objectKey());
    out.put("name", view.originalName());
    out.put("size", view.sizeBytes());
    String extension = OssSettingsSupport.extensionOf(view.originalName());
    out.put("extension", extension);
    out.put("category", view.category());
    out.put("provider", view.provider());
    return out;
  }

  static String normalizeCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return CATEGORY_FILES;
    }
    String c = raw.trim().toLowerCase(Locale.ROOT);
    return switch (c) {
      case CATEGORY_MEDIA, CATEGORY_FILES, CATEGORY_OTHER -> c;
      case "releases" -> CATEGORY_OTHER; // 预留，v1 并入 other
      default -> throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_UPLOAD_CATEGORY);
    };
  }
}
