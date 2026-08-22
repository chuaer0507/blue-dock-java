package com.bluedock.system.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LetterAvatarControllerTest {
  @Test
  void normalizeFallsBackAndTrimsHan() {
    assertEquals("D", LetterAvatarController.normalizeName(""));
    assertEquals("D", LetterAvatarController.normalizeName("测试账号"));
    assertEquals("任务", LetterAvatarController.normalizeName("我的任务"));
    assertEquals("AB", LetterAvatarController.normalizeName("abcdef"));
  }
}
