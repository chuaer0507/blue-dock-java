package com.bluedock.system.service;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import org.springframework.stereotype.Component;

@Component
public class AdminGuard {
  private final UserAccountRepository users;

  public AdminGuard(UserAccountRepository users) {
    this.users = users;
  }

  public void requireAdmin() {
    long userId = AuthContext.requireUserId();
    if (!isAdmin(userId)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.ADMIN_REQUIRED);
    }
  }

  public boolean isAdmin(long userId) {
    String identity =
        users.findByUserId(userId).map(u -> u.getIdentity() == null ? "" : u.getIdentity()).orElse("");
    return identity.contains("admin");
  }
}
