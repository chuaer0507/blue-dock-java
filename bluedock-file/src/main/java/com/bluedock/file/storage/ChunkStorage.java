package com.bluedock.file.storage;

import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.file.config.UploadProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class ChunkStorage {
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final UploadProperties props;
  private final ObjectStorage objectStorage;

  public ChunkStorage(UploadProperties props, ObjectStorage objectStorage) {
    this.props = props;
    this.objectStorage = objectStorage;
  }

  public Path chunkDir(long userId, String uploadId) {
    return Path.of(props.getBaseDir(), "tmp", "chunks", String.valueOf(userId), uploadId);
  }

  public Path chunkFile(long userId, String uploadId, int index) {
    return chunkDir(userId, uploadId).resolve(index + ".part");
  }

  public void writeChunk(long userId, String uploadId, int index, InputStream in)
      throws IOException {
    Path dir = chunkDir(userId, uploadId);
    Files.createDirectories(dir);
    Path target = chunkFile(userId, uploadId, index);
    try (OutputStream out =
        Files.newOutputStream(
            target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      in.transferTo(out);
    }
  }

  /**
   * 拼装分片后写入 {@link ObjectStorage}，返回对象键（相对路径）。
   * 临时分片仍落在 {@code bluedock.upload.base-dir/tmp}。
   */
  public String mergeToObject(
      long userId, String uploadId, int chunkCount, long fileId, String type) throws IOException {
    String yearMonth = LocalDate.now().format(YM);
    String safeType = (type == null || type.isBlank()) ? "file" : type;
    String objectKey = "file/" + safeType + "/" + yearMonth + "/" + fileId + "/content";
    Path temp = chunkDir(userId, uploadId).resolve("_merged.tmp");
    try (OutputStream out =
        Files.newOutputStream(
            temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      for (int i = 0; i < chunkCount; i++) {
        Path part = chunkFile(userId, uploadId, i);
        if (!Files.exists(part)) {
          throw new IOException("missing chunk " + i);
        }
        try (InputStream in = Files.newInputStream(part)) {
          in.transferTo(out);
        }
      }
    }
    long size = Files.size(temp);
    try (InputStream in = Files.newInputStream(temp)) {
      objectStorage.put(objectKey, in, size, null);
    } finally {
      Files.deleteIfExists(temp);
    }
    return objectKey;
  }

  public Path mergeToFinal(long userId, String uploadId, int chunkCount, long fileId, String type)
      throws IOException {
    String key = mergeToObject(userId, uploadId, chunkCount, fileId, type);
    return resolveRelative(key);
  }

  public void deleteSession(long userId, String uploadId) {
    Path dir = chunkDir(userId, uploadId);
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // best-effort cleanup
                }
              });
    } catch (IOException ignored) {
      // best-effort cleanup
    }
  }

  public String relativePath(Path absolute) {
    Path base = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
    Path abs = absolute.toAbsolutePath().normalize();
    return base.relativize(abs).toString().replace('\\', '/');
  }

  /** Resolve a stored relative path under the upload base; rejects path traversal. */
  public Path resolveRelative(String relative) {
    if (relative == null || relative.isBlank()) {
      throw new IllegalArgumentException("empty path");
    }
    Path base = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
    Path resolved = base.resolve(relative.replace('\\', '/')).normalize();
    if (!resolved.startsWith(base)) {
      throw new IllegalArgumentException("path escapes base");
    }
    return resolved;
  }
}
