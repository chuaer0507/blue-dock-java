package com.bluedock.file.share;

import com.bluedock.common.user.UserShareFileBridge;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.service.FileAccessService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UserShareFileBridgeImpl implements UserShareFileBridge {
  private final FileRepository files;
  private final FileAccessService access;

  public UserShareFileBridgeImpl(FileRepository files, FileAccessService access) {
    this.files = files;
    this.access = access;
  }

  @Override
  public List<Map<String, Object>> listFolders(long userId, long parentId) {
    long parent = Math.max(0L, parentId);
    if (parent > 0) {
      access.requireReadable(parent, userId);
    }
    List<FileEntry> entries =
        parent == 0 ? files.listByParent(userId, 0L) : files.listByParentAny(parent);
    List<Map<String, Object>> out = new ArrayList<>();
    for (FileEntry f : entries) {
      if (f == null || !"folder".equalsIgnoreCase(f.getType() == null ? "" : f.getType())) {
        continue;
      }
      if (f.getId() == parent) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", f.getId());
      row.put("name", f.getName() == null ? "" : f.getName());
      row.put("isShared", f.getIsShared() == 1);
      out.add(row);
    }
    return out;
  }
}
