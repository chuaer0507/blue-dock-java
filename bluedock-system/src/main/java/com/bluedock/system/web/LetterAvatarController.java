package com.bluedock.system.web;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 字母头像 PNG（匿名）：{@code GET /avatar?name=&size=}。 */
@RestController
public class LetterAvatarController {
  private static final Pattern BRACKETS = Pattern.compile("[\\(\\)（）\\[\\]【】{}［］<>＜＞『「』」].*?[\\)\\]】}］>＞』」]");
  private static final Pattern NOISE =
      Pattern.compile(
          "测试|测试号|账号|帐号|账户|系统|管理员|用户|官方|客服|bot|admin|test|user|system|vip",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SYMBOLS =
      Pattern.compile("[\\-_=/\\\\|~@#$%^&*\\s\\t\\n\\r。，、；：？！．…′″℃.,;:?!\"'`★☆○●◇◆□■△▲]+");

  @GetMapping(value = "/avatar", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> avatar(
      @RequestParam(required = false) String name,
      @RequestParam(required = false, defaultValue = "128") int size)
      throws Exception {
    int s = Math.min(512, Math.max(16, size));
    String label = normalizeName(name);
    Color bg = backgroundOf(label);
    BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(bg);
      g.fillRect(0, 0, s, s);
      g.setColor(Color.WHITE);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, s / 3)));
      var fm = g.getFontMetrics();
      int x = (s - fm.stringWidth(label)) / 2;
      int y = (s - fm.getHeight()) / 2 + fm.getAscent();
      g.drawString(label, x, y);
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(21)))
        .contentType(MediaType.IMAGE_PNG)
        .body(out.toByteArray());
  }

  static String normalizeName(String raw) {
    String name = raw == null ? "" : raw.trim();
    name = BRACKETS.matcher(name).replaceAll("");
    name = NOISE.matcher(name).replaceAll("");
    name = SYMBOLS.matcher(name).replaceAll("");
    name = name.trim();
    if (name.isEmpty()) {
      return "D";
    }
    if (name.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
      int len = name.codePointCount(0, name.length());
      if (len > 2) {
        name = name.substring(name.offsetByCodePoints(0, len - 2));
      }
    } else {
      int len = name.codePointCount(0, name.length());
      if (len > 2) {
        name = name.substring(0, name.offsetByCodePoints(0, 2));
      }
      name = name.toUpperCase(Locale.ROOT);
    }
    return name.isEmpty() ? "D" : name;
  }

  private static Color backgroundOf(String name) {
    try {
      byte[] dig =
          MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8));
      int h = ((dig[0] & 0xff) << 16) | ((dig[1] & 0xff) << 8) | (dig[2] & 0xff);
      float hue = (h % 360) / 360f;
      return Color.getHSBColor(hue, 0.55f, 0.55f);
    } catch (Exception e) {
      return new Color(0x3b82f6);
    }
  }
}
