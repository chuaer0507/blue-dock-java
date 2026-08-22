package com.bluedock.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({UploadProperties.class, OfficeProperties.class})
public class FileModuleConfig {}
