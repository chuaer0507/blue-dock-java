package com.bluedock.user.meeting.agora;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Agora RTC AccessToken v006。 */
public final class AgoraAccessToken {
  public static final int PRIVILEGE_JOIN = 1;
  public static final int PRIVILEGE_PUBLISH_AUDIO = 2;
  public static final int PRIVILEGE_PUBLISH_VIDEO = 3;
  public static final int PRIVILEGE_PUBLISH_DATA = 4;

  private AgoraAccessToken() {}

  public static String build(
      String appId, String appCertificate, String channelName, int agoraUserId, int privilegeExpireSeconds) {
    if (appId == null || appId.isBlank() || appCertificate == null || appCertificate.isBlank()) {
      throw new IllegalArgumentException("appId/certificate required");
    }
    String agoraUserIdStr = agoraUserId == 0 ? "" : Integer.toString(agoraUserId);
    int salt = new SecureRandom().nextInt(100_000);
    int ts = (int) (System.currentTimeMillis() / 1000L) + Math.max(3600, privilegeExpireSeconds);

    Map<Integer, Integer> privileges = new LinkedHashMap<>();
    privileges.put(PRIVILEGE_JOIN, 0);
    privileges.put(PRIVILEGE_PUBLISH_AUDIO, 0);
    privileges.put(PRIVILEGE_PUBLISH_VIDEO, 0);
    privileges.put(PRIVILEGE_PUBLISH_DATA, 0);

    byte[] packedMessage = packMessage(salt, ts, privileges);
    byte[] toSign =
        concat(
            appId.getBytes(StandardCharsets.UTF_8),
            channelName.getBytes(StandardCharsets.UTF_8),
            agoraUserIdStr.getBytes(StandardCharsets.UTF_8),
            packedMessage);
    byte[] sig = hmacSha256(appCertificate.getBytes(StandardCharsets.UTF_8), toSign);

    ByteBuffer content = ByteBuffer.allocate(2 + sig.length + 4 + 4 + 2 + packedMessage.length);
    content.order(ByteOrder.LITTLE_ENDIAN);
    content.putShort((short) sig.length);
    content.put(sig);
    content.putInt(crc32(channelName));
    content.putInt(crc32(agoraUserIdStr));
    content.putShort((short) packedMessage.length);
    content.put(packedMessage);

    return "006" + appId + Base64.getEncoder().encodeToString(content.array());
  }

  private static byte[] packMessage(int salt, int ts, Map<Integer, Integer> privileges) {
    ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 2 + privileges.size() * 6);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(salt);
    buf.putInt(ts);
    buf.putShort((short) privileges.size());
    for (Map.Entry<Integer, Integer> e : privileges.entrySet()) {
      buf.putShort(e.getKey().shortValue());
      buf.putInt(e.getValue());
    }
    return buf.array();
  }

  private static int crc32(String s) {
    CRC32 crc = new CRC32();
    crc.update(s.getBytes(StandardCharsets.UTF_8));
    return (int) crc.getValue();
  }

  private static byte[] hmacSha256(byte[] key, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data);
    } catch (Exception e) {
      throw new IllegalStateException("hmac failed", e);
    }
  }

  private static byte[] concat(byte[]... parts) {
    int n = 0;
    for (byte[] p : parts) {
      n += p.length;
    }
    byte[] out = new byte[n];
    int i = 0;
    for (byte[] p : parts) {
      System.arraycopy(p, 0, out, i, p.length);
      i += p.length;
    }
    return out;
  }
}
