package com.bluedock.common.deploy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** 读写 deploy/.env.* 凭据行（中文前缀注释 或 KEY=value；文件不存在则新建）。 */
public final class DeployEnvWriter {

  public static final String ADMIN_USERNAME_PREFIX = "#admin账号：";
  public static final String ADMIN_PASSWORD_PREFIX = "#admin密码：";

  private DeployEnvWriter() {}

  public static void upsertCredentials(Path envFile, Map<String, String> prefixedValues)
      throws IOException {
    if (prefixedValues == null || prefixedValues.isEmpty()) {
      return;
    }
    List<String> lines = readOrCreate(envFile);

    Map<String, String> remaining = new LinkedHashMap<>(prefixedValues);
    List<String> updated = new ArrayList<>(lines.size() + remaining.size());
    for (String line : lines) {
      String prefix = matchingPrefix(line, remaining.keySet());
      if (prefix != null) {
        updated.add(prefix + remaining.remove(prefix));
      } else {
        updated.add(line);
      }
    }
    for (Map.Entry<String, String> entry : remaining.entrySet()) {
      updated.add(entry.getKey() + entry.getValue());
    }

    write(envFile, updated);
  }

  /**
   * 确保 {@code KEY=value} 存在：已有非空值则原样返回且不改写；缺失或空值则写入 {@code
   * valueIfAbsent.get()} 并返回新值。
   */
  public static String ensureEnvVar(Path envFile, String key, Supplier<String> valueIfAbsent)
      throws IOException {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("env key required");
    }
    if (valueIfAbsent == null) {
      throw new IllegalArgumentException("valueIfAbsent required");
    }
    List<String> lines = readOrCreate(envFile);
    String prefix = key + "=";
    String exportPrefix = "export " + key + "=";

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String raw = stripEnvAssignment(line, prefix, exportPrefix);
      if (raw == null) {
        continue;
      }
      String existing = unquote(raw.trim());
      if (!existing.isEmpty()) {
        return existing;
      }
      String generated = requireNonBlank(valueIfAbsent.get(), key);
      lines.set(i, key + "=" + generated);
      write(envFile, lines);
      return generated;
    }

    String generated = requireNonBlank(valueIfAbsent.get(), key);
    lines.add(key + "=" + generated);
    write(envFile, lines);
    return generated;
  }

  /** 读取 {@code KEY=} 非空值；不存在或为空返回 {@code null}。 */
  public static String readEnvVar(Path envFile, String key) throws IOException {
    if (key == null || key.isBlank() || !Files.exists(envFile)) {
      return null;
    }
    String prefix = key + "=";
    String exportPrefix = "export " + key + "=";
    for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
      String raw = stripEnvAssignment(line, prefix, exportPrefix);
      if (raw == null) {
        continue;
      }
      String value = unquote(raw.trim());
      if (!value.isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private static List<String> readOrCreate(Path envFile) throws IOException {
    List<String> lines = new ArrayList<>();
    if (Files.exists(envFile)) {
      lines.addAll(Files.readAllLines(envFile, StandardCharsets.UTF_8));
    } else {
      Path parent = envFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
    }
    return lines;
  }

  private static void write(Path envFile, List<String> lines) throws IOException {
    Files.write(envFile, lines, StandardCharsets.UTF_8);
    restrictPermissions(envFile);
  }

  private static String stripEnvAssignment(String line, String prefix, String exportPrefix) {
    if (line == null) {
      return null;
    }
    String trimmed = line.trim();
    if (trimmed.startsWith("#")) {
      return null;
    }
    if (trimmed.startsWith(exportPrefix)) {
      return trimmed.substring(exportPrefix.length());
    }
    if (trimmed.startsWith(prefix)) {
      return trimmed.substring(prefix.length());
    }
    return null;
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private static String requireNonBlank(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("generated value blank for " + key);
    }
    return value.trim();
  }

  private static String matchingPrefix(String line, Set<String> prefixes) {
    for (String prefix : prefixes) {
      if (line.startsWith(prefix)) {
        return prefix;
      }
    }
    return null;
  }

  private static void restrictPermissions(Path envFile) {
    try {
      Set<PosixFilePermission> perms =
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(envFile, perms);
    } catch (UnsupportedOperationException | IOException ignored) {
      // Windows 等环境忽略
    }
  }
}
