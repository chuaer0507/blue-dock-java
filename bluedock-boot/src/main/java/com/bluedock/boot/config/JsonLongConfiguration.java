package com.bluedock.boot.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** HTTP JSON 中的业务 BIGINT 统一输出为字符串，避免 JavaScript 精度丢失。 */
@Configuration
public class JsonLongConfiguration {

  @Bean
  JsonMapperBuilderCustomizer jsonLongAsStringCustomizer() {
    return builder -> {
      SimpleModule module = new SimpleModule("long-as-string");
      module.addSerializer(Long.class, ToStringSerializer.instance);
      module.addSerializer(Long.TYPE, ToStringSerializer.instance);
      builder.addModule(module);
    };
  }
}
