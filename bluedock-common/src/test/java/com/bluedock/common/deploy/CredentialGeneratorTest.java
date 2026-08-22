package com.bluedock.common.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CredentialGeneratorTest {

  @Test
  void adminEmailLooksLikeLocalMailbox() {
    String email = CredentialGenerator.adminEmail();
    assertTrue(email.startsWith("admin_"));
    assertTrue(email.endsWith("@bluedock.local"));
    assertEquals(8, email.substring("admin_".length(), email.indexOf('@')).length());
  }

  @Test
  void randomPasswordHasRequestedLength() {
    assertEquals(16, CredentialGenerator.randomPassword(16).length());
  }
}
