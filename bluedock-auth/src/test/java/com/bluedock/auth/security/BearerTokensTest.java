package com.bluedock.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BearerTokensTest {
  @Test
  void extract_bearer() {
    assertEquals("abc", BearerTokens.extract("Bearer abc"));
    assertEquals("abc", BearerTokens.extract("bearer abc"));
    assertEquals("raw", BearerTokens.extract("raw"));
    assertNull(BearerTokens.extract(null));
    assertNull(BearerTokens.extract("Bearer "));
  }
}
