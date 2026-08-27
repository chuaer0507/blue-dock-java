package com.bluedock.worker.index.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 非 Web Worker：确保 Jackson ObjectMapper 可用（Boot 4 无 web 时可能不自动注册）。 */
@Configuration
public class WorkerJacksonConfig {
  @Bean
  @ConditionalOnMissingBean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
