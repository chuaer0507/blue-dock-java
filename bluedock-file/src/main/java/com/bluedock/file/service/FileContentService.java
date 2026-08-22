package com.bluedock.file.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.domain.FileContent;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.web.dto.FileContentHistoryItem;
import com.bluedock.file.web.dto.FileContentView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileContentService {
  private static final int MAX_HISTORY = 100;
  private static final int MAX_CONTENT_BYTES = 2_097_152;
  private static final Set<String> EDITABLE =
      Set.of("document", "mind", "drawio", "word", "excel", "ppt", "txt", "code");

  private final FileRepository files;
  private final FileContentRepository contents;
  private final FileAccessService access;
  private final UploadService uploads;

  public FileContentService(
      FileRepository files,
      FileContentRepository contents,
      FileAccessService access,
      UploadService uploads) {
    this.files = files;
    this.contents = contents;
    this.access = access;
    this.uploads = uploads;
  }

  public FileContentView content(long id) {
    long userId = AuthContext.requireUserId();
    FileEntry file = requireEditable(id, userId, false);
    return contents
        .findLatest(file.getId())
        .map(FileContentView::from)
        .orElseGet(() -> emptyView(file.getId(), userId));
  }

  @Transactional
  public FileContentView save(long id, String content) {
    long userId = AuthContext.requireUserId();
    FileEntry file = requireEditable(id, userId, true);
    String body = content == null ? "" : content;
    int bytes = body.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > MAX_CONTENT_BYTES) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_CONTENT_TOO_LARGE);
    }
    return insertVersion(file.getId(), userId, body, bytes);
  }

  public List<FileContentHistoryItem> history(long id, Integer take) {
    long userId = AuthContext.requireUserId();
    FileEntry file = requireEditable(id, userId, false);
    int limit = take == null || take <= 0 ? 50 : Math.min(take, MAX_HISTORY);
    return contents.listHistory(file.getId(), limit).stream()
        .map(FileContentHistoryItem::from)
        .toList();
  }

  @Transactional
  public FileContentView restore(long id, long contentId) {
    long userId = AuthContext.requireUserId();
    FileEntry file = requireEditable(id, userId, true);
    FileContent ver =
        contents
            .findActive(contentId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodes.NOT_FOUND, I18nKeys.FILE_CONTENT_NOT_FOUND));
    if (ver.getFileId() != file.getId()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_CONTENT_NOT_FOUND);
    }
    String body = ver.getContent() == null ? "" : ver.getContent();
    int bytes = body.getBytes(StandardCharsets.UTF_8).length;
    return insertVersion(file.getId(), userId, body, bytes);
  }

  /** 将分片上传会话合并到已有文件，并写入一版内容元数据。 */
  @Transactional
  public FileContentView uploadFromSession(long id, String uploadId) {
    long userId = AuthContext.requireUserId();
    if (uploadId == null || uploadId.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_UPLOAD_ID_REQUIRED);
    }
    FileEntry file = access.requireWritable(id, userId);
    if ("folder".equals(file.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_TYPE_INVALID);
    }
    UploadService.MergedBlob blob =
        uploads.mergeIntoExisting(file.getId(), file.getType(), uploadId.trim());
    LocalDateTime now = LocalDateTime.now();
    files.updateStorage(file.getId(), blob.path(), blob.hash(), blob.size(), now);
    String meta =
        "{\"url\":\""
            + blob.path().replace("\"", "")
            + "\",\"type\":\""
            + (file.getType() == null ? "" : file.getType())
            + "\",\"ext\":\""
            + (file.getExtension() == null ? "" : file.getExtension())
            + "\"}";
    return insertVersion(file.getId(), userId, meta, blob.size());
  }

  private FileContentView insertVersion(long fileId, long userId, String body, long size) {
    LocalDateTime now = LocalDateTime.now();
    FileContent row = new FileContent();
    row.setId(IdGenerator.nextId());
    row.setFileId(fileId);
    row.setContent(body);
    row.setText(body);
    row.setSize(size);
    row.setUserId(userId);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    contents.insert(row);
    files.updateSize(fileId, size, now);
    return FileContentView.from(row);
  }

  private FileEntry requireEditable(long id, long userId, boolean write) {
    FileEntry f = write ? access.requireWritable(id, userId) : access.requireReadable(id, userId);
    String type = f.getType() == null ? "" : f.getType();
    if (!EDITABLE.contains(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_TYPE_INVALID);
    }
    return f;
  }

  private static FileContentView emptyView(long fileId, long userId) {
    return new FileContentView(0L, fileId, "", "", 0L, userId, null);
  }
}
