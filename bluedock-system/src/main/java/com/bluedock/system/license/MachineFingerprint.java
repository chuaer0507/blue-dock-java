package com.bluedock.system.license;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 本机 SN / MAC 探针（容器环境可配置覆盖）。 */
public final class MachineFingerprint {
  private static final Logger log = LoggerFactory.getLogger(MachineFingerprint.class);

  private MachineFingerprint() {}

  /** 稳定机器码：优先配置；否则 hostname + 首个 MAC 的短哈希。 */
  public static String machineSn(String configured) {
    if (configured != null && !configured.isBlank()) {
      return configured.trim();
    }
    String host = "unknown";
    try {
      host = java.net.InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
      // keep unknown
    }
    List<String> machineMacAddresses = macAddresses();
    String seed =
        host + "|" + (machineMacAddresses.isEmpty() ? "nomac" : machineMacAddresses.get(0));
    return "SN-" + sha256Short(seed);
  }

  public static List<String> macAddresses() {
    Set<String> out = new LinkedHashSet<>();
    try {
      Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
      if (ifaces == null) {
        return List.of();
      }
      while (ifaces.hasMoreElements()) {
        NetworkInterface nif = ifaces.nextElement();
        if (nif == null || nif.isLoopback() || nif.isVirtual() || !nif.isUp()) {
          continue;
        }
        byte[] hw = nif.getHardwareAddress();
        if (hw == null || hw.length < 6) {
          continue;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hw.length; i++) {
          if (i > 0) {
            sb.append(':');
          }
          sb.append(String.format(Locale.ROOT, "%02X", hw[i]));
        }
        String macAddress = sb.toString();
        if (isMacAddress(macAddress)) {
          out.add(macAddress);
        }
      }
    } catch (Exception e) {
      log.debug("enumerate mac failed: {}", e.toString());
    }
    return Collections.unmodifiableList(new ArrayList<>(out));
  }

  public static boolean isMacAddress(String macAddress) {
    if (macAddress == null) {
      return false;
    }
    return macAddress.matches("(?i)^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
  }

  private static String sha256Short(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig).substring(0, 16).toUpperCase(Locale.ROOT);
    } catch (Exception e) {
      return Integer.toHexString(raw.hashCode()).toUpperCase(Locale.ROOT);
    }
  }
}
