package com.bluedock.common.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeployEnvWriterTest {

  @TempDir Path tempDir;

  @Test
  void upsertCredentialsWritesAdminLines() throws Exception {
    Path env = tempDir.resolve(".env.dev");
    Files.writeString(env, "BLUEDOCK_VERSION=v1.0.0\n#admin账号：\n#admin密码：\n", StandardCharsets.UTF_8);

    Map<String, String> credentials = new LinkedHashMap<>();
    credentials.put(DeployEnvWriter.ADMIN_USERNAME_PREFIX, "admin_abc12345@bluedock.local");
    credentials.put(DeployEnvWriter.ADMIN_PASSWORD_PREFIX, "SecretPass123");
    DeployEnvWriter.upsertCredentials(env, credentials);

    List<String> lines = Files.readAllLines(env, StandardCharsets.UTF_8);
    assertEquals("BLUEDOCK_VERSION=v1.0.0", lines.get(0));
    assertEquals("#admin账号：admin_abc12345@bluedock.local", lines.get(1));
    assertEquals("#admin密码：SecretPass123", lines.get(2));
  }

  @Test
  void ensureEnvVarGeneratesWhenMissing() throws Exception {
    Path env = tempDir.resolve(".env.dev");
    Files.writeString(env, "#admin账号：x\n", StandardCharsets.UTF_8);

    AtomicInteger calls = new AtomicInteger();
    String token =
        DeployEnvWriter.ensureEnvVar(
            env,
            "BLUEDOCK_DEMO_TOKEN",
            () -> {
              calls.incrementAndGet();
              return "tok-generated";
            });

    assertEquals("tok-generated", token);
    assertEquals(1, calls.get());
    assertTrue(Files.readString(env, StandardCharsets.UTF_8).contains("BLUEDOCK_DEMO_TOKEN=tok-generated"));
  }

  @Test
  void ensureEnvVarSkipsWhenPresent() throws Exception {
    Path env = tempDir.resolve(".env.prod");
    Files.writeString(env, "BLUEDOCK_DEMO_TOKEN=existing-secret\n", StandardCharsets.UTF_8);

    AtomicInteger calls = new AtomicInteger();
    String token =
        DeployEnvWriter.ensureEnvVar(
            env,
            "BLUEDOCK_DEMO_TOKEN",
            () -> {
              calls.incrementAndGet();
              return "should-not-run";
            });

    assertEquals("existing-secret", token);
    assertEquals(0, calls.get());
  }

  @Test
  void readEnvVarIgnoresCommentedLine() throws Exception {
    Path env = tempDir.resolve(".env.dev");
    Files.writeString(
        env, "# BLUEDOCK_DEMO_TOKEN=commented\nFOO=1\n", StandardCharsets.UTF_8);

    assertNull(DeployEnvWriter.readEnvVar(env, "BLUEDOCK_DEMO_TOKEN"));
  }
}
