package com.bluedock.system.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class OssSettingBootstrapRunner implements ApplicationRunner {
  private final OssSettingService oss;

  public OssSettingBootstrapRunner(OssSettingService oss) {
    this.oss = oss;
  }

  @Override
  public void run(ApplicationArguments args) {
    oss.applyStoredOnStartup();
  }
}
