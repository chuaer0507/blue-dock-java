package com.bluedock.file.chat;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.oss.OssExtensionChecker;
import com.bluedock.common.upload.DialogChatFileSink;
import com.bluedock.common.upload.UploadSizeLimit;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DialogChatFileSinkImpl implements DialogChatFileSink {
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final Set<String> IMAGE_EXT =
      Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

  private final FileRepository files;
  private final UploadProperties props;
  private final ObjectStorage objectStorage;
  private final ObjectProvider<OssExtensionChecker> extensionChecker;
  private final ObjectProvider<UploadSizeLimit> sizeLimit;

  public DialogChatFileSinkImpl(
      FileRepository files,
      UploadProperties props,
      ObjectStorage objectStorage,
      ObjectProvider<OssExtensionChecker> extensionChecker,
      ObjectProvider<UploadSizeLimit> sizeLimit) {
    this.files = files;
    this.props = props;
    this.objectStorage = objectStorage;
    this.extensionChecker = extensionChecker;
    this.sizeLimit = sizeLimit;
  }

  @Override
  @Transactional
  public Saved save(long userId, long dialogId, String filename, long size, InputStream content) {
    String name = filename == null ? "" : filename.trim();
    if (name.isEmpty() || name.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NAME_INVALID);
    }
    if (size <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_SIZE_INVALID);
    }
    long max =
        sizeLimit.getIfAvailable() == null
            ? props.maxFileSizeBytes()
            : sizeLimit.getObject().maxBytesOrDefault(props.maxFileSizeBytes());
    if (size > max) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_TOO_LARGE);
    }
    if (content == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    OssExtensionChecker checker = extensionChecker.getIfAvailable();
    if (checker != null) {
      checker.assertAllowed(name);
    }

    String extension = extensionOf(name);
    String type = IMAGE_EXT.contains(extension) ? "picture" : "file";
    long fileId = IdGenerator.nextId();
    String yearMonth = LocalDate.now().format(YM);
    String relPath =
        PathJoin.chat(dialogId, yearMonth, fileId);
    String hash;
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      try (DigestInputStream din = new DigestInputStream(content, md)) {
        din.transferTo(buf);
      }
      byte[] bytes = buf.toByteArray();
      if (bytes.length <= 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
      }
      hash = HexFormat.of().formatHex(md.digest());
      size = bytes.length;
      objectStorage.put(relPath, new ByteArrayInputStream(bytes), size, null);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }

    LocalDateTime now = LocalDateTime.now();
    FileEntry f = new FileEntry();
    f.setId(fileId);
    f.setParentId(0L);
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
    return new Saved(fileId, name, type, extension, size, relPath);
  }

  private static String extensionOf(String name) {
    int i = name.lastIndexOf('.');
    if (i < 0 || i == name.length() - 1) {
      return "";
    }
    return name.substring(i + 1).toLowerCase(Locale.ROOT);
  }

  private static final class PathJoin {
    static String chat(long dialogId, String yearMonth, long fileId) {
      return "chat/" + dialogId + "/" + yearMonth + "/" + fileId + "/content";
    }
  }
}
