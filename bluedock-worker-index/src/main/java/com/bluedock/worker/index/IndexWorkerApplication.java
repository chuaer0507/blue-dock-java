package com.bluedock.worker.index;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/** Kafka worker — 无 HTTP。消费 {@code bluedock.search.index}。 */
@SpringBootApplication(scanBasePackages = "com.bluedock.worker.index")
@EnableKafka
public class IndexWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(IndexWorkerApplication.class, args);
  }
}
