package com.bluedock.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonLongConfigurationTest {

  @Test
  void serializes_long_values_as_strings_without_changing_int_values() throws Exception {
    JsonMapper.Builder builder = JsonMapper.builder();
    new JsonLongConfiguration().jsonLongAsStringCustomizer().customize(builder);
    JsonMapper mapper = builder.build();

    String json = mapper.writeValueAsString(Map.of("id", 350905970450370560L, "code", 0));

    assertThat(json).contains("\"id\":\"350905970450370560\"");
    assertThat(json).contains("\"code\":0");
  }
}
