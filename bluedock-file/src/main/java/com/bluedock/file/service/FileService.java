package com.bluedock.file.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FileView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {
  private static final int MAX_CHILDREN = 300;
  private static final int MAX_SEARCH = 100;
  private static final long MAX_FETCH_BYTES = 2_097_152L;

  private final FileRepository files;
  private final FileAccessService access;
  private final FileShareService shares;
  private final ObjectProvider<BrowseRecorder> browseRecorder;
  private final ObjectProvider<ChunkStorage> chunkStorage;

  public FileService(
      FileRepository files,
      FileAccessService access,
      FileShareService shares,
      ObjectProvider<BrowseRecorder> browseRecorder,
      ObjectProvider<ChunkStorage> chunkStorage) {
    this.files = files;
    this.access = access;
    this.shares = shares;
    this.browseRecorder = browseRecorder;
    this.chunkStorage = chunkStorage;
  }

  public List<FileView> lists(Long parentId) {
    long userId = AuthContext.requireUserId();
    long parent = parentId == null ? 0L : parentId;
    if (parent <= 0) {
      Map<Long, FileEntry> merged = new LinkedHashMap<>();
      for (FileEntry f : files.listByParent(userId, 0L)) {
        merged.put(f.getId(), f);
      }
      for (FileEntry f : files.listSharedRoots(userId)) {
        merged.putIfAbsent(f.getId(), f);
      }
      return merged.values().stream().map(FileView::from).toList();
    }
    access.requireReadable(parent, userId);
    FileEntry folder = files.findActive(parent).orElseThrow();
    if (folder.getUserId() == userId) {
      return files.listByParent(userId, parent).stream().map(FileView::from).toList();
    }
    return files.listByParentAny(parent).stream().map(FileView::from).toList();
  }

  public FileView one(long id) {
    long userId = AuthContext.requireUserId();
    FileEntry f = access.requireReadable(id, userId);
    BrowseRecorder recorder = browseRecorder.getIfAvailable();
    if (recorder != null) {
      recorder.recordFile(userId, id);
    }
    return FileView.from(f);
  }

  public String fetch(Long id, String path) {
    long userId = AuthContext.requireUserId();
    FileEntry f;
    if (id != null && id > 0) {
      f = access.requireReadable(id, userId);
    } else if (path != null && !path.isBlank()) {
      f =
          files
              .findByUserAndPath(userId, path.trim())
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NOT_FOUND);
    }
    if ("folder".equals(f.getType())) {
      return "";
    }
    String rel = f.getPath() == null ? "" : f.getPath().trim();
    if (rel.isEmpty()) {
      return "";
    }
    ChunkStorage storage = chunkStorage.getIfAvailable();
    if (storage == null) {
      return "";
    }
    try {
      var abs = storage.resolveRelative(rel);
      if (!Files.isRegularFile(abs)) {
        return "";
      }
      long size = Files.size(abs);
      if (size > MAX_FETCH_BYTES) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_DENIED);
      }
      return Files.readString(abs, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | IOException e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
  }

  /** 鉴权后解析二进制文件绝对路径（预览 / 下载）。 */
  public RawFile raw(long id) {
    long userId = AuthContext.requireUserId();
    FileEntry f = access.requireReadable(id, userId);
    if ("folder".equals(f.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_TYPE_INVALID);
    }
    String rel = f.getPath() == null ? "" : f.getPath().trim();
    if (rel.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
    ChunkStorage storage = chunkStorage.getIfAvailable();
    if (storage == null) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
    try {
      var abs = storage.resolveRelative(rel);
      if (!Files.isRegularFile(abs)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
      }
      return new RawFile(abs, f.getName(), f.getExtension() == null ? "" : f.getExtension());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND);
    }
  }

  public record RawFile(java.nio.file.Path path, String name, String extension) {}

  public List<FileView> search(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    String k = key == null ? "" : key.trim();
    if (k.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_KEY_REQUIRED);
    }
    int limit = take == null || take <= 0 ? 50 : Math.min(take, MAX_SEARCH);
    Map<Long, FileEntry> merged = new LinkedHashMap<>();
    for (FileEntry f : files.searchByName(userId, k, limit)) {
      merged.put(f.getId(), f);
    }
    for (FileEntry root : files.listSharedRoots(userId)) {
      if (root.getName() != null && root.getName().contains(k)) {
        merged.putIfAbsent(root.getId(), root);
      }
    }
    List<FileView> out = new ArrayList<>();
    for (FileEntry f : merged.values()) {
      out.add(FileView.from(f));
      if (out.size() >= limit) {
        break;
      }
    }
    return out;
  }

  @Transactional
  public FileView add(Long id, Long parentId, String name, String type) {
    long userId = AuthContext.requireUserId();
    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NAME_INVALID);
    }
    String t = type == null || type.isBlank() ? "folder" : type.trim();
    if (!"folder".equals(t) && !"document".equals(t)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NAME_INVALID);
    }

    if (id != null && id > 0) {
      FileEntry existing = access.requireWritable(id, userId);
      files.rename(id, n, LocalDateTime.now());
      existing.setName(n);
      existing.setUpdatedAt(LocalDateTime.now());
      return FileView.from(existing);
    }

    long parent = parentId == null ? 0L : parentId;
    requireParentFolderWritable(userId, parent);
    long ownerId = userId;
    if (parent > 0) {
      FileEntry p = files.findActive(parent).orElseThrow();
      ownerId = p.getUserId();
      if (files.countByParent(ownerId, parent) >= MAX_CHILDREN) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
      }
    } else if (files.countByParent(userId, parent) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }

    LocalDateTime now = LocalDateTime.now();
    FileEntry f = new FileEntry();
    f.setId(IdGenerator.nextId());
    f.setParentId(parent);
    f.setName(n);
    f.setType(t);
    f.setExtension("document".equals(t) ? "md" : "");
    f.setSize(0);
    f.setHash("");
    f.setPath("");
    f.setUserId(ownerId);
    f.setCreatedUserId(userId);
    f.setIsShared(0);
    f.setCreatedAt(now);
    f.setUpdatedAt(now);
    files.insert(f);
    return FileView.from(f);
  }

  @Transactional
  public FileView copy(long id, Long parentId) {
    long userId = AuthContext.requireUserId();
    FileEntry src = access.requireReadable(id, userId);
    long parent = parentId == null ? src.getParentId() : parentId;
    requireParentFolderWritable(userId, parent);
    if (isDescendantOrSelf(parent, id)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_MOVE_INVALID);
    }
    long ownerId = userId;
    if (parent > 0) {
      ownerId = files.findActive(parent).orElseThrow().getUserId();
    }
    if (files.countByParent(ownerId, parent) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }
    return FileView.from(copyRecursive(userId, ownerId, src, parent));
  }

  @Transactional
  public FileView move(long id, long parentId) {
    long userId = AuthContext.requireUserId();
    FileEntry src = access.requireOwner(id, userId);
    if (src.getParentId() == parentId) {
      return FileView.from(src);
    }
    if (isDescendantOrSelf(parentId, id)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_MOVE_INVALID);
    }
    requireParentFolderWritable(userId, parentId);
    long ownerId = userId;
    if (parentId > 0) {
      ownerId = files.findActive(parentId).orElseThrow().getUserId();
    }
    if (files.countByParent(ownerId, parentId) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }
    LocalDateTime now = LocalDateTime.now();
    files.move(id, parentId, now);
    src.setParentId(parentId);
    src.setUpdatedAt(now);
    return FileView.from(src);
  }

  @Transactional
  public void remove(long id) {
    long userId = AuthContext.requireUserId();
    access.requireOwnerOrCreator(id, userId);
    LocalDateTime now = LocalDateTime.now();
    FileEntry root = files.findActive(id).orElseThrow();
    softDeleteTreeWithShare(root.getUserId(), id, now);
  }

  /** 当前用户回收站根列表（软删且父级未删）。 */
  public List<FileView> trash() {
    long userId = AuthContext.requireUserId();
    return files.listTrashRoots(userId).stream().map(FileView::from).toList();
  }

  /**
   * 恢复软删文件(夹)及子树。父级仍删或缺失时挂到根目录。共享关系删除时已清理，不自动恢复。
   */
  @Transactional
  public FileView restore(long id) {
    long userId = AuthContext.requireUserId();
    FileEntry root = access.requireOwnerOrCreatorDeleted(id, userId);
    LocalDateTime now = LocalDateTime.now();
    long targetParent = root.getParentId();
    if (targetParent > 0 && files.findActive(targetParent).isEmpty()) {
      targetParent = 0;
    }
    if (targetParent > 0
        && files.countByParent(root.getUserId(), targetParent) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }
    if (targetParent == 0 && files.countByParent(root.getUserId(), 0L) >= MAX_CHILDREN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }
    files.restoreWithParent(id, targetParent, now);
    restoreDeletedChildren(root.getUserId(), id, now);
    FileEntry restored =
        files
            .findActive(id)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    return FileView.from(restored);
  }

  private void restoreDeletedChildren(long ownerId, long parentId, LocalDateTime at) {
    for (Long childId : files.listDeletedChildIds(ownerId, parentId)) {
      files.clearDeleted(childId, at);
      restoreDeletedChildren(ownerId, childId, at);
    }
  }

  private void softDeleteTreeWithShare(long ownerId, long rootId, LocalDateTime at) {
    shares.cleanupShare(rootId, at);
    files.softDelete(rootId, at);
    for (Long child : files.listChildIds(ownerId, rootId)) {
      softDeleteTreeWithShare(ownerId, child, at);
    }
  }

  private FileEntry copyRecursive(long actorId, long ownerId, FileEntry src, long parent) {
    LocalDateTime now = LocalDateTime.now();
    FileEntry copy = new FileEntry();
    copy.setId(IdGenerator.nextId());
    copy.setParentId(parent);
    copy.setName(src.getName());
    copy.setType(src.getType());
    copy.setExtension(src.getExtension() == null ? "" : src.getExtension());
    copy.setSize(src.getSize());
    copy.setHash(src.getHash() == null ? "" : src.getHash());
    copy.setPath(src.getPath() == null ? "" : src.getPath());
    copy.setUserId(ownerId);
    copy.setCreatedUserId(actorId);
    copy.setIsShared(0);
    copy.setCreatedAt(now);
    copy.setUpdatedAt(now);
    files.insert(copy);

    if ("folder".equals(src.getType())) {
      List<FileEntry> children =
          src.getUserId() == actorId
              ? files.listByParent(src.getUserId(), src.getId())
              : files.listByParentAny(src.getId());
      for (FileEntry child : children) {
        if (files.countByParent(ownerId, copy.getId()) >= MAX_CHILDREN) {
          break;
        }
        copyRecursive(actorId, ownerId, child, copy.getId());
      }
    }
    return copy;
  }

  private void requireParentFolderWritable(long userId, long parent) {
    if (parent <= 0) {
      return;
    }
    FileEntry p = access.requireWritable(parent, userId);
    if (!"folder".equals(p.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_PARENT_INVALID);
    }
  }

  private boolean isDescendantOrSelf(long candidate, long rootId) {
    if (candidate <= 0) {
      return false;
    }
    if (candidate == rootId) {
      return true;
    }
    long cur = candidate;
    for (int i = 0; i < 64; i++) {
      FileEntry node =
          files
              .findActive(cur)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
      if (node.getParentId() == rootId) {
        return true;
      }
      if (node.getParentId() <= 0) {
        return false;
      }
      cur = node.getParentId();
    }
    return false;
  }
}
