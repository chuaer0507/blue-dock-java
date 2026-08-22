package com.bluedock.auth.service;

import com.bluedock.auth.web.dto.CaptchaJsonView;
import com.bluedock.common.redis.RedisKeys;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

  private final StringRedisTemplate redis;
  private final Random random = new Random();

  public CaptchaService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public CaptchaJsonView createJson() {
    Issued issued = issue();
    return new CaptchaJsonView(issued.key(), "data:image/png;base64," + issued.pngBase64());
  }

  /** 生成验证码；返回 key + 原始 PNG 字节（供 codeimg）。 */
  public Issued createImage() {
    return issue();
  }

  public boolean verifyAndConsume(String key, String code) {
    if (key == null || key.isBlank() || code == null || code.isBlank()) {
      return false;
    }
    String redisKey = RedisKeys.captcha(key);
    String stored = redis.opsForValue().get(redisKey);
    if (stored == null) {
      return false;
    }
    redis.delete(redisKey);
    return stored.equalsIgnoreCase(code.trim());
  }

  private Issued issue() {
    String code = randomCode();
    String key = UUID.randomUUID().toString();
    redis.opsForValue().set(RedisKeys.captcha(key), code, TTL);
    byte[] png = renderPng(code);
    return new Issued(key, png, Base64.getEncoder().encodeToString(png));
  }

  private String randomCode() {
    StringBuilder sb = new StringBuilder(4);
    for (int i = 0; i < 4; i++) {
      sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return sb.toString();
  }

  private byte[] renderPng(String code) {
    int width = 120;
    int height = 44;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(230 + random.nextInt(20), 230 + random.nextInt(20), 230 + random.nextInt(20)));
    g.fillRect(0, 0, width, height);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    g.setColor(new Color(20 + random.nextInt(80), 20 + random.nextInt(80), 20 + random.nextInt(80)));
    g.drawString(code, 18, 30);
    g.dispose();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("captcha render failed", e);
    }
  }

  public record Issued(String key, byte[] pngBytes, String pngBase64) {}
}
