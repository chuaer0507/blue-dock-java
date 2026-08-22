package com.bluedock.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bluedock.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthContextTest {
  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void requireUserId_whenSet() {
    AuthContext.set(new AuthUser(42L));
    assertEquals(42L, AuthContext.requireUserId());
  }

  @Test
  void requireUserId_whenMissing() {
    assertThrows(BusinessException.class, AuthContext::requireUserId);
  }
}
