package com.bluedock.file.service;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.storage.ChunkStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理过期分片临时目录：Redis 会话已失效，或目录 mtime 超过 24h。
 *
 * <p>cancel/merge 会主动删盘；本调度兜底孤儿目录。
 */
@Component
public class UploadTempCleanupScheduler {
  private static final Logger log = LoggerFactory.getLogger(UploadTempCleanupScheduler.class);
  private static final Duration MAX_AGE = Duration.ofHours(24);

  private final UploadProperties props;
  private final ChunkStorage chunks;
  private final StringRedisTemplate redis;

  public UploadTempCleanupScheduler(
      UploadProperties props, ChunkStorage chunks, StringRedisTemplate redis) {
    this.props = props;
    this.chunks = chunks;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.upload.temp-cleanup-ms:3600000}")
  public void tick() {
    Path root = Path.of(props.getBaseDir(), "tmp", "chunks");
    if (!Files.isDirectory(root)) {
      return;
    }
    Instant cutoff = Instant.now().minus(MAX_AGE);
    int removed = 0;
    try (Stream<Path> users = Files.list(root)) {
      for (Path userDir : users.toList()) {
        if (!Files.isDirectory(userDir)) {
          continue;
        }
        long userId;
        try {
          userId = Long.parseLong(userDir.getFileName().toString());
        } catch (NumberFormatException e) {
          continue;
        }
        try (Stream<Path> sessions = Files.list(userDir)) {
          for (Path sessionDir : sessions.toList()) {
            if (!Files.isDirectory(sessionDir)) {
              continue;
            }
            String uploadId = sessionDir.getFileName().toString();
            boolean redisGone = !Boolean.TRUE.equals(redis.hasKey(RedisKeys.upload(uploadId)));
            boolean tooOld = isOlderThan(sessionDir, cutoff);
            if (redisGone || tooOld) {
              chunks.deleteSession(userId, uploadId);
              removed++;
            }
          }
        }
        // 空用户目录
        try (Stream<Path> left = Files.list(userDir)) {
          if (left.findAny().isEmpty()) {
            Files.deleteIfExists(userDir);
          }
        }
      }
    } catch (IOException e) {
      log.warn("upload temp cleanup failed: {}", e.toString());
      return;
    }
    if (removed > 0) {
      log.info("upload temp cleanup removed={} sessions", removed);
    }
  }

  private static boolean isOlderThan(Path dir, Instant cutoff) {
    try {
      Instant mtime = Files.getLastModifiedTime(dir).toInstant();
      return mtime.isBefore(cutoff);
    } catch (IOException e) {
      return false;
    }
  }
}
