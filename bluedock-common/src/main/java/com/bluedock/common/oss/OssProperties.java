package com.bluedock.common.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储：{@code local} 落盘 {@code public/{type}/...}；云厂商上传后 URL 为 {@code
 * {public-base-url}/{key}}。
 */
@ConfigurationProperties(prefix = "bluedock.oss")
public class OssProperties {

  /** local | huawei | aliyun | tencent | qiniu */
  private String provider = "local";

  /** 公开访问前缀（无尾斜杠）。local 默认可用 releases 的 public-base-url 兜底。 */
  private String publicBaseUrl = "";

  private final Local local = new Local();
  private final Huawei huawei = new Huawei();
  private final Aliyun aliyun = new Aliyun();
  private final Tencent tencent = new Tencent();
  private final Qiniu qiniu = new Qiniu();

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public Local getLocal() {
    return local;
  }

  public Huawei getHuawei() {
    return huawei;
  }

  public Aliyun getAliyun() {
    return aliyun;
  }

  public Tencent getTencent() {
    return tencent;
  }

  public Qiniu getQiniu() {
    return qiniu;
  }

  public OssProvider resolvedProvider() {
    return OssProvider.fromConfig(provider);
  }

  /** 拼公开 URL：{@code base + "/" + key}。 */
  public String buildPublicUrl(String key) {
    String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    String rel = key == null ? "" : key.replaceAll("^/+", "");
    if (base.isEmpty()) {
      return "/" + rel;
    }
    return base + "/" + rel;
  }

  public static class Local {
    /** 本地根目录；其下按类型分子目录 releases/ media/ 等。默认空则 {@code ./data/uploads}。 */
    private String storagePath = "";

    public String getStoragePath() {
      return storagePath;
    }

    public void setStoragePath(String storagePath) {
      this.storagePath = storagePath;
    }
  }

  public static class Huawei {
    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "";

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }
  }

  public static class Aliyun {
    private String endpoint = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String bucket = "";

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
      return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
      return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
      this.accessKeySecret = accessKeySecret;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }
  }

  public static class Tencent {
    private String region = "";
    private String secretId = "";
    private String secretKey = "";
    private String bucket = "";

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getSecretId() {
      return secretId;
    }

    public void setSecretId(String secretId) {
      this.secretId = secretId;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }
  }

  public static class Qiniu {
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "";
    /** z0 / z1 / z2 / na0 / as0 */
    private String region = "z0";

    public String getAccessKey() {
      return accessKey;
    }

    public void setAccessKey(String accessKey) {
      this.accessKey = accessKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }
  }
}
