package com.bluedock.file.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.oss.OssExtensionChecker;
import com.bluedock.common.upload.TaskAttachmentSink;
import com.bluedock.common.upload.UploadSizeLimit;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FileView;
import com.bluedock.file.web.dto.UploadChunkView;
import com.bluedock.file.web.dto.UploadInitView;
import com.bluedock.file.web.dto.UploadMergeView;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private static final Duration SESSION_TTL = Duration.ofHours(24);
  private static final int MAX_CHILDREN = 300;

  private final StringRedisTemplate redis;
  private final FileRepository files;
  private final ChunkStorage storage;
  private final UploadProperties props;
  private final ObjectProvider<TaskAttachmentSink> taskAttachments;
  private final ObjectProvider<OssExtensionChecker> extensionChecker;
  private final ObjectProvider<UploadSizeLimit> sizeLimit;

  public UploadService(
      StringRedisTemplate redis,
      FileRepository files,
      ChunkStorage storage,
      UploadProperties props,
      ObjectProvider<TaskAttachmentSink> taskAttachments,
      ObjectProvider<OssExtensionChecker> extensionChecker,
      ObjectProvider<UploadSizeLimit> sizeLimit) {
    this.redis = redis;
    this.files = files;
    this.storage = storage;
    this.props = props;
    this.taskAttachments = taskAttachments;
    this.extensionChecker = extensionChecker;
    this.sizeLimit = sizeLimit;
  }

  public UploadInitView init(
      String hash, long size, String name, String scene, Long parentId, Long taskId) {
    long userId = AuthContext.requireUserId();
    String h = hash == null ? "" : hash.trim().toLowerCase();
    if (!h.matches("[a-f0-9]{32}")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_HASH_INVALID);
    }
    if (size <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_SIZE_INVALID);
    }
    if (size > maxUploadBytes()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TOO_LARGE);
    }
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NAME_INVALID);
    }
    OssExtensionChecker checker = extensionChecker.getIfAvailable();
    if (checker != null) {
      checker.assertAllowed(n);
    }
    String sc = scene == null || scene.isBlank() ? "file_cabinet" : scene.trim();
    boolean taskScene = TaskAttachmentSink.SCENE.equals(sc);
    if (taskScene && (taskId == null || taskId <= 0)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TASK_ID_REQUIRED);
    }

    long folderId = parentId == null ? 0L : parentId;
    if (!taskScene && folderId > 0) {
      FileEntry parent =
          files
              .findActive(folderId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID));
      if (parent.getUserId() != userId || !"folder".equals(parent.getType())) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
      }
    }

    if (!taskScene) {
      var existing = files.findByUserAndHash(userId, h);
      if (existing.isPresent()) {
        return new UploadInitView(true, "", 0, 0, List.of(), FileView.from(existing.get()));
      }
    }

    long chunkSize = props.getChunkSize();
    int chunkCount = (int) ((size + chunkSize - 1) / chunkSize);
    String uploadId = UUID.randomUUID().toString().replace("-", "");
    String key = RedisKeys.upload(uploadId);
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                "userId", String.valueOf(userId),
                "hash", h,
                "size", String.valueOf(size),
                "name", n,
                "scene", sc,
                "parentId", String.valueOf(folderId),
                "taskId", String.valueOf(taskId == null ? 0L : taskId),
                "chunkSize", String.valueOf(chunkSize),
                "chunkCount", String.valueOf(chunkCount)));
    redis.expire(key, SESSION_TTL);
    redis.expire(RedisKeys.uploadChunks(uploadId), SESSION_TTL);

    return new UploadInitView(false, uploadId, chunkSize, chunkCount, List.of(), null);
  }

  public UploadChunkView chunk(String uploadId, int index, MultipartFile blob) {
    long userId = AuthContext.requireUserId();
    String id = requireUploadId(uploadId);
    Map<Object, Object> meta = loadMeta(id);
    requireOwner(meta, userId);
    int chunkCount = Integer.parseInt(String.valueOf(meta.get("chunkCount")));
    if (index < 0 || index >= chunkCount) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_INDEX);
    }
    if (blob == null || blob.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    try {
      storage.writeChunk(userId, id, index, blob.getInputStream());
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    String chunksKey = RedisKeys.uploadChunks(id);
    redis.opsForSet().add(chunksKey, String.valueOf(index));
    redis.expire(chunksKey, SESSION_TTL);
    redis.expire(RedisKeys.upload(id), SESSION_TTL);
    return new UploadChunkView(id, received(id));
  }

  @Transactional
  public UploadMergeView merge(String uploadId) {
    long userId = AuthContext.requireUserId();
    String id = requireUploadId(uploadId);
    Map<Object, Object> meta = loadMeta(id);
    requireOwner(meta, userId);

    int chunkCount = Integer.parseInt(String.valueOf(meta.get("chunkCount")));
    List<Integer> got = received(id);
    if (got.size() < chunkCount) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_INCOMPLETE);
    }

    String name = String.valueOf(meta.get("name"));
    String hash = String.valueOf(meta.get("hash"));
    long size = Long.parseLong(String.valueOf(meta.get("size")));
    long parentId = Long.parseLong(String.valueOf(meta.get("parentId")));
    String scene = String.valueOf(meta.get("scene"));
    long taskId =
        meta.get("taskId") == null ? 0L : Long.parseLong(String.valueOf(meta.get("taskId")));
    boolean taskScene = TaskAttachmentSink.SCENE.equals(scene);

    if (taskScene) {
      return mergeTaskAttachment(userId, id, name, size, taskId, chunkCount);
    }

    if (files.countByParent(userId, parentId) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }

    var instant = files.findByUserAndHash(userId, hash);
    if (instant.isPresent()) {
      cleanup(userId, id);
      return UploadMergeView.forCabinet(FileView.from(instant.get()));
    }

    long fileId = IdGenerator.nextId();
    String type = guessType(name, scene);
    String extension = extensionOf(name);
    LocalDateTime now = LocalDateTime.now();
    String relPath;
    try {
      relPath = storage.mergeToObject(userId, id, chunkCount, fileId, type);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_INCOMPLETE);
    }

    FileEntry f = new FileEntry();
    f.setId(fileId);
    f.setParentId(parentId);
    f.setName(name);
    f.setType(type);
    f.setExtension(extension);
    f.setSize(size);
    f.setHash(hash);
    f.setPath(relPath);
    f.setUserId(userId);
    f.setCreatedUserId(userId);
    f.setIsShared(0);
    f.setCreatedAt(now);
    f.setUpdatedAt(now);
    files.insert(f);
    cleanup(userId, id);
    return UploadMergeView.forCabinet(FileView.from(f));
  }

  /**
   * 将已完成分片会话合并到已有文件（覆盖存储路径），用于 content/upload。
   *
   * @return [relPath, hash, size]
   */
  public MergedBlob mergeIntoExisting(long fileId, String type, String uploadId) {
    long userId = AuthContext.requireUserId();
    String id = requireUploadId(uploadId);
    Map<Object, Object> meta = loadMeta(id);
    requireOwner(meta, userId);

    int chunkCount = Integer.parseInt(String.valueOf(meta.get("chunkCount")));
    List<Integer> got = received(id);
    if (got.size() < chunkCount) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_INCOMPLETE);
    }
    String hash = String.valueOf(meta.get("hash"));
    long size = Long.parseLong(String.valueOf(meta.get("size")));
    String safeType = (type == null || type.isBlank()) ? "file" : type;
    String relPath;
    try {
      relPath = storage.mergeToObject(userId, id, chunkCount, fileId, safeType);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_INCOMPLETE);
    }
    cleanup(userId, id);
    return new MergedBlob(relPath, hash, size);
  }

  public record MergedBlob(String path, String hash, long size) {}

  private UploadMergeView mergeTaskAttachment(
      long userId, String uploadId, String name, long size, long taskId, int chunkCount) {
    TaskAttachmentSink sink = taskAttachments.getIfAvailable();
    if (sink == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TASK_SINK_MISSING);
    }
    if (taskId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TASK_ID_REQUIRED);
    }
    long fileId = IdGenerator.nextId();
    String extension = extensionOf(name);
    String type = guessType(name, TaskAttachmentSink.SCENE);
    String relPath;
    try {
      relPath = storage.mergeToObject(userId, uploadId, chunkCount, fileId, type);
    } catch (IOException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_INCOMPLETE);
    }
    Map<String, Object> saved = sink.save(taskId, name, size, extension, relPath, "");
    cleanup(userId, uploadId);
    return UploadMergeView.forTask(saved);
  }

  public void cancel(String uploadId) {
    long userId = AuthContext.requireUserId();
    if (uploadId == null || uploadId.isBlank()) {
      return;
    }
    String id = uploadId.trim();
    Map<Object, Object> meta = redis.opsForHash().entries(RedisKeys.upload(id));
    if (meta.isEmpty()) {
      return;
    }
    if (!String.valueOf(userId).equals(String.valueOf(meta.get("userId")))) {
      return;
    }
    cleanup(userId, id);
  }

  private long maxUploadBytes() {
    long yaml = props.maxFileSizeBytes();
    UploadSizeLimit limit = sizeLimit.getIfAvailable();
    return limit == null ? yaml : limit.maxBytesOrDefault(yaml);
  }

  private void cleanup(long userId, String uploadId) {
    storage.deleteSession(userId, uploadId);
    redis.delete(RedisKeys.upload(uploadId));
    redis.delete(RedisKeys.uploadChunks(uploadId));
  }

  private Map<Object, Object> loadMeta(String uploadId) {
    Map<Object, Object> meta = redis.opsForHash().entries(RedisKeys.upload(uploadId));
    if (meta.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.UPLOAD_SESSION_MISSING);
    }
    return meta;
  }

  private static void requireOwner(Map<Object, Object> meta, long userId) {
    if (!String.valueOf(userId).equals(String.valueOf(meta.get("userId")))) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
  }

  private static String requireUploadId(String uploadId) {
    if (uploadId == null || uploadId.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_ID_REQUIRED);
    }
    return uploadId.trim();
  }

  private List<Integer> received(String uploadId) {
    Set<String> members = redis.opsForSet().members(RedisKeys.uploadChunks(uploadId));
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    List<Integer> list =
        members.stream().map(Integer::parseInt).sorted().collect(Collectors.toCollection(ArrayList::new));
    return Collections.unmodifiableList(list);
  }

  private static String extensionOf(String name) {
    int i = name.lastIndexOf('.');
    if (i < 0 || i == name.length() - 1) {
      return "";
    }
    return name.substring(i + 1).toLowerCase();
  }

  private static String guessType(String name, String scene) {
    if ("image".equals(scene)) {
      return "picture";
    }
    String extension = extensionOf(name);
    return switch (extension) {
      case "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "picture";
      case "md", "markdown" -> "document";
      case "txt" -> "txt";
      case "pdf" -> "pdf";
      case "doc", "docx" -> "word";
      case "xls", "xlsx" -> "excel";
      case "ppt", "pptx" -> "ppt";
      case "zip", "rar", "7z" -> "archive";
      case "mp3", "mp4", "mov", "wav" -> "media";
      default -> "file";
    };
  }
}
