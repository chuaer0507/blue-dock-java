package com.bluedock.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.upload")
public class UploadProperties {
  private String baseDir = "./data/uploads";
  private long chunkSize = 5_242_880L;
  private long maxFileSizeMb = 1024L;

  public String getBaseDir() {
    return baseDir;
  }

  public void setBaseDir(String baseDir) {
    this.baseDir = baseDir;
  }

  public long getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(long chunkSize) {
    this.chunkSize = chunkSize;
  }

  public long getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public void setMaxFileSizeMb(long maxFileSizeMb) {
    this.maxFileSizeMb = maxFileSizeMb;
  }

  public long maxFileSizeBytes() {
    return maxFileSizeMb * 1024L * 1024L;
  }
}
