package com.bluedock.system.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SystemProperties.class, AppsProperties.class, BlueDockPublicProperties.class})
public class SystemModuleConfig {}
