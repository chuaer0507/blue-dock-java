package com.bluedock.worker.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Kafka worker — 无 HTTP。消费 {@code bluedock.notify.send} / {@code bluedock.userBot.webhook}。 */
@SpringBootApplication(scanBasePackages = "com.bluedock.worker.notify")
@EnableKafka
@EnableScheduling
public class NotifyWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotifyWorkerApplication.class, args);
  }
}
