package com.bluedock.common.util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdGeneratorTest {
  @Test
  void nextId_isPositiveAndUnique() {
    long a = IdGenerator.nextId();
    long b = IdGenerator.nextId();
    assertTrue(a > 0);
    assertTrue(b > 0);
    assertNotEquals(a, b);
  }
}
