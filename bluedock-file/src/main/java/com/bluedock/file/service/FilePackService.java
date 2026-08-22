package com.bluedock.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileContent;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FilePackView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FilePackService {
  private static final int MAX_FILES = 100;
  private static final int MAX_DEPTH = 20;
  private static final Duration PACK_TTL = Duration.ofHours(2);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final FileRepository files;
  private final FileContentRepository contents;
  private final FileAccessService access;
  private final ChunkStorage storage;
  private final UploadProperties props;
  private final StringRedisTemplate redis;

  public FilePackService(
      FileRepository files,
      FileContentRepository contents,
      FileAccessService access,
      ChunkStorage storage,
      UploadProperties props,
      StringRedisTemplate redis) {
    this.files = files;
    this.contents = contents;
    this.access = access;
    this.storage = storage;
    this.props = props;
    this.redis = redis;
  }

  public FilePackView pack(String ids) {
    long userId = AuthContext.requireUserId();
    List<Long> idList = parseIds(ids);
    if (idList.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_IDS_REQUIRED);
    }

    List<FileEntry> roots = new ArrayList<>();
    for (Long id : idList) {
      roots.add(access.requireReadable(id, userId));
    }

    String packId = UUID.randomUUID().toString().replace("-", "");
    String zipriorityName = zipriorityName(roots);
    Path zipPath = Path.of(props.getBaseDir(), "tmp", "packs", String.valueOf(userId), packId + ".zip");
    try {
      Files.createDirectories(zipPath.getParent());
      Counter counter = new Counter();
      try (OutputStream out = Files.newOutputStream(zipPath);
          ZipOutputStream zos = new ZipOutputStream(out)) {
        Set<String> usedNames = new LinkedHashSet<>();
        for (FileEntry root : roots) {
          String entryBase = uniqueName(usedNames, safeName(root.getName()));
          addNode(zos, root, entryBase, 0, counter, userId);
        }
        if (counter.files == 0 && counter.dirs == 0) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_EMPTY);
        }
      }
      long size = Files.size(zipPath);
      if (size > props.maxFileSizeBytes()) {
        Files.deleteIfExists(zipPath);
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_TOO_LARGE);
      }
      String rel = storage.relativePath(zipPath);
      saveMeta(packId, userId, zipriorityName, rel, size);
      return new FilePackView(packId, zipriorityName, rel, size);
    } catch (BusinessException e) {
      try {
        Files.deleteIfExists(zipPath);
      } catch (IOException ignored) {
        // best-effort
      }
      throw e;
    } catch (IOException e) {
      try {
        Files.deleteIfExists(zipPath);
      } catch (IOException ignored) {
        // best-effort
      }
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_EMPTY);
    }
  }

  public FilePackView status(String packId) {
    long userId = AuthContext.requireUserId();
    String raw = redis.opsForValue().get(RedisKeys.filePack(packId));
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_PACK_NOT_FOUND);
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> meta = JSON.readValue(raw, Map.class);
      long owner = ((Number) meta.get("userId")).longValue();
      if (owner != userId) {
        throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
      }
      return new FilePackView(
          packId,
          String.valueOf(meta.getOrDefault("name", "pack.zip")),
          String.valueOf(meta.getOrDefault("path", "")),
          ((Number) meta.getOrDefault("size", 0)).longValue());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_PACK_NOT_FOUND);
    }
  }

  /** Resolve pack zip path for streaming download; caller must check ownership via status. */
  public Path resolvePackFile(String packId) {
    FilePackView view = status(packId);
    try {
      Path abs = storage.resolveRelative(view.path());
      if (!Files.isRegularFile(abs)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_PACK_NOT_FOUND);
      }
      return abs;
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_PACK_NOT_FOUND);
    }
  }

  private void addNode(
      ZipOutputStream zos,
      FileEntry node,
      String entryName,
      int depth,
      Counter counter,
      long userId)
      throws IOException {
    if (depth > MAX_DEPTH) {
      return;
    }
    if ("folder".equals(node.getType())) {
      String dir = entryName.endsWith("/") ? entryName : entryName + "/";
      zos.putNextEntry(new ZipEntry(dir));
      zos.closeEntry();
      counter.dirs++;
      List<FileEntry> children =
          node.getUserId() == userId
              ? files.listByParent(node.getUserId(), node.getId())
              : files.listByParentAny(node.getId());
      Set<String> used = new LinkedHashSet<>();
      for (FileEntry child : children) {
        if (counter.files >= MAX_FILES) {
          break;
        }
        String childName = uniqueName(used, safeName(child.getName()));
        addNode(zos, child, dir + childName, depth + 1, counter, userId);
      }
      return;
    }

    if (counter.files >= MAX_FILES) {
      return;
    }
    byte[] body = readBody(node);
    if (body == null) {
      return;
    }
    zos.putNextEntry(new ZipEntry(entryName));
    zos.write(body);
    zos.closeEntry();
    counter.files++;
    counter.bytes += body.length;
    if (counter.bytes > props.maxFileSizeBytes()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_TOO_LARGE);
    }
  }

  private byte[] readBody(FileEntry node) throws IOException {
    String rel = node.getPath() == null ? "" : node.getPath().trim();
    if (!rel.isEmpty()) {
      try {
        Path abs = storage.resolveRelative(rel);
        if (Files.isRegularFile(abs)) {
          long size = Files.size(abs);
          if (size > props.maxFileSizeBytes()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_TOO_LARGE);
          }
          try (InputStream in = Files.newInputStream(abs)) {
            return in.readAllBytes();
          }
        }
      } catch (IllegalArgumentException ignored) {
        // fall through to content
      }
    }
    return contents
        .findLatest(node.getId())
        .map(FileContent::getContent)
        .map(c -> c == null ? "" : c)
        .map(c -> c.getBytes(StandardCharsets.UTF_8))
        .orElseGet(
            () -> {
              if ("document".equals(node.getType())
                  || "txt".equals(node.getType())
                  || "code".equals(node.getType())) {
                return new byte[0];
              }
              return null;
            });
  }

  private void saveMeta(String packId, long userId, String name, String path, long size) {
    try {
      String json =
          JSON.writeValueAsString(
              Map.of("userId", userId, "name", name, "path", path, "size", size));
      redis.opsForValue().set(RedisKeys.filePack(packId), json, PACK_TTL);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PACK_EMPTY);
    }
  }

  private static String zipriorityName(List<FileEntry> roots) {
    if (roots.size() == 1) {
      return safeName(roots.get(0).getName()) + ".zip";
    }
    return "files.zip";
  }

  private static String safeName(String name) {
    String n = name == null || name.isBlank() ? "file" : name.trim();
    n = n.replace('\\', '_').replace('/', '_').replace("..", "_");
    if (n.length() > 180) {
      n = n.substring(0, 180);
    }
    return n;
  }

  private static String uniqueName(Set<String> used, String base) {
    if (used.add(base)) {
      return base;
    }
    for (int i = 2; i < 1000; i++) {
      String candidate = base + "(" + i + ")";
      if (used.add(candidate)) {
        return candidate;
      }
    }
    String fallback = base + "-" + UUID.randomUUID().toString().substring(0, 8);
    used.add(fallback);
    return fallback;
  }

  private static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (String part : raw.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        long v = Long.parseLong(part.trim());
        if (v > 0) {
          ids.add(v);
        }
      } catch (NumberFormatException e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_IDS_REQUIRED);
      }
    }
    return new ArrayList<>(ids);
  }

  private static final class Counter {
    int files;
    int dirs;
    long bytes;
  }
}
