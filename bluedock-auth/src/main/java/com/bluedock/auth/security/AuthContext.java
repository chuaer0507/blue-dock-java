package com.bluedock.auth.security;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;

/** ThreadLocal 当前用户。Filter 必须在 finally 中 clear。 */
public final class AuthContext {
  private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

  private AuthContext() {}

  public static void set(AuthUser user) {
    HOLDER.set(user);
  }

  public static AuthUser get() {
    return HOLDER.get();
  }

  public static long requireUserId() {
    AuthUser user = HOLDER.get();
    if (user == null) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.UNAUTHORIZED_EXPIRED);
    }
    return user.userId();
  }

  public static void clear() {
    HOLDER.remove();
  }
}
