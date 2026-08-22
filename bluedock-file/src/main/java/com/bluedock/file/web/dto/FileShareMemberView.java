package com.bluedock.file.web.dto;

import com.bluedock.file.domain.FileUser;

public record FileShareMemberView(long userId, int permission) {
  public static FileShareMemberView from(FileUser u) {
    return new FileShareMemberView(u.getUserId(), u.getPermission());
  }
}
