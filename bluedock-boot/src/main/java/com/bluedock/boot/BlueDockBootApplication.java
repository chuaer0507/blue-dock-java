package com.bluedock.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bluedock")
@EnableScheduling
public class BlueDockBootApplication {
  public static void main(String[] args) {
    SpringApplication.run(BlueDockBootApplication.class, args);
  }
}
