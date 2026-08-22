package com.bluedock.boot.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 解析超管凭据写入的 deploy/.env.dev 或 .env.prod 路径。 */
@Component
public class DeployEnvPathResolver {

  private final String envFileOverride;
  private final String activeProfiles;

  public DeployEnvPathResolver(
      @Value("${bluedock.deploy.env-file:}") String envFileOverride,
      @Value("${spring.profiles.active:}") String activeProfiles) {
    this.envFileOverride = envFileOverride;
    this.activeProfiles = activeProfiles;
  }

  public Path resolve() {
    if (envFileOverride != null && !envFileOverride.isBlank()) {
      return Path.of(envFileOverride.trim());
    }
    String fileName = isProdProfile() ? ".env.prod" : ".env.dev";
    String userDir = System.getProperty("user.dir", ".");
    List<Path> candidates = new ArrayList<>();
    candidates.add(Path.of(userDir, "deploy", fileName));
    candidates.add(Path.of(userDir, "..", "deploy", fileName));
    candidates.add(Path.of("deploy", fileName));
    for (Path candidate : candidates) {
      Path normalized = candidate.normalize();
      Path parent = normalized.getParent();
      if (parent != null && Files.exists(parent)) {
        return normalized;
      }
    }
    return Path.of(userDir, "deploy", fileName).normalize();
  }

  private boolean isProdProfile() {
    if (activeProfiles == null || activeProfiles.isBlank()) {
      return false;
    }
    for (String part : activeProfiles.split(",")) {
      if ("prod".equalsIgnoreCase(part.trim())) {
        return true;
      }
    }
    return false;
  }
}
