package com.bluedock.boot.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 开发期（版本含 SNAPSHOT）启动前 {@code repair} 同步 checksum，再 {@code migrate}。
 * 注意：{@code repair} 只改历史表，不重跑已应用的 Vn；改 V1 后若列结构漂移，须重建库（drop database / compose
 * volume）。
 * 正式版不 repair，校验失败即阻止启动。
 */
@Configuration
public class FlywayDevRepairConfig {

  @Bean
  @ConditionalOnMissingBean(FlywayMigrationStrategy.class)
  FlywayMigrationStrategy flywayMigrationStrategy(
      @Value("${bluedock.version:1.0.0}") String version) {
    boolean snapshot = version != null && version.contains("SNAPSHOT");
    return (Flyway flyway) -> {
      if (snapshot) {
        flyway.repair();
      }
      flyway.migrate();
    };
  }
}
