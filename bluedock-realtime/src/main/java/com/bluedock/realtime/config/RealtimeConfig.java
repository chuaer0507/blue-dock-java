package com.bluedock.realtime.config;

import com.bluedock.common.kafka.ConsumerGroups;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
public class RealtimeConfig {
  /**
   * 扇出需广播：每个 boot 实例独立 groupId，否则同组只会有一台收到消息。
   */
  @Bean(name = "realtimeConsumerGroupId")
  public String realtimeConsumerGroupId() {
    return ConsumerGroups.REALTIME + "-" + UUID.randomUUID();
  }
}
