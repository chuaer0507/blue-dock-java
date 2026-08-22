package com.bluedock.system.oss;

/**
 * OSS 配置文档（存 {@code bluedock_settings.name=oss}，亦为 system setting API body）。
 *
 * <p>密钥字段在 GET 响应中可能为 {@code ********}；PUT 时传空或该占位则保留原值。
 *
 * <p>字段与行为见 {@code docs/platform/oss-settings.md}。
 */
public record OssSettingsDocument(
    String provider,
    String nameType,
    String linkType,
    String allowExtensions,
    String protocol,
    String domain,
    String publicBaseUrl,
    Local local,
    Huawei huawei,
    Aliyun aliyun,
    Tencent tencent,
    Qiniu qiniu) {

  public static final String MASK = "********";

  public static final String NAME_HASH = "hash";
  public static final String NAME_DATE_RANDOM = "dateRandom";

  public static final String LINK_SIMPLE = "simple";
  public static final String LINK_FULL = "full";
  public static final String LINK_SIMPLE_COMPRESS = "simpleCompress";
  public static final String LINK_FULL_COMPRESS = "fullCompress";

  public static final String PROTO_FOLLOW = "follow";
  public static final String PROTO_HTTP = "http";
  public static final String PROTO_HTTPS = "https";
  public static final String PROTO_PATH = "path";
  public static final String PROTO_AUTO = "auto";

  public static final String DEFAULT_ALLOW_EXTENSIONS =
      "png,jpg,jpeg,gif,webp,zip,pdf,doc,docx,xls,xlsx,ppt,pptx,mp4,txt";

  public record Local(String storagePath) {}

  public record Huawei(String endpoint, String accessKey, String secretKey, String bucket) {}

  public record Aliyun(
      String endpoint, String accessKeyId, String accessKeySecret, String bucket) {}

  public record Tencent(String region, String secretId, String secretKey, String bucket) {}

  public record Qiniu(String accessKey, String secretKey, String bucket, String region) {}
}
