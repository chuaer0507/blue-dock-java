package com.bluedock.system.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.oss.OssExtensionChecker;
import com.bluedock.common.oss.OssProperties;
import com.bluedock.common.oss.OssProvider;
import com.bluedock.common.oss.OssStorageFactory;
import com.bluedock.common.oss.RuntimeObjectStorage;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.oss.OssSettingsDocument;
import com.bluedock.system.oss.OssSettingsSupport;
import com.bluedock.system.repo.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 系统 OSS 配置：`bluedock_settings.name=oss` + {@link RuntimeObjectStorage} 热切换。 */
@Service
public class OssSettingService implements OssExtensionChecker {
  public static final String SETTING_NAME = "oss";

  private static final Logger log = LoggerFactory.getLogger(OssSettingService.class);

  private final SettingRepository settings;
  private final RuntimeObjectStorage runtimeObjectStorage;
  private final OssProperties bootOssProperties;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redis;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public OssSettingService(
      SettingRepository settings,
      RuntimeObjectStorage runtimeObjectStorage,
      OssProperties bootOssProperties,
      ObjectMapper objectMapper,
      StringRedisTemplate redis,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.runtimeObjectStorage = runtimeObjectStorage;
    this.bootOssProperties = bootOssProperties;
    this.objectMapper = objectMapper;
    this.redis = redis;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  public OssSettingsDocument get() {
    adminGuard.requireAdmin();
    return maskSecrets(OssSettingsSupport.normalize(loadEffective(), false));
  }

  @Transactional
  public OssSettingsDocument save(OssSettingsDocument incoming) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    if (incoming == null || !StringUtils.hasText(incoming.provider())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_PROVIDER_REQUIRED);
    }
    OssSettingsDocument merged = mergeSecrets(loadEffective(), incoming);
    OssSettingsDocument normalized = OssSettingsSupport.normalize(merged, true);
    OssProperties props = toProperties(normalized);
    try {
      ObjectStorage next =
          OssStorageFactory.create(props, nullToEmpty(bootOssProperties.getPublicBaseUrl()));
      persist(normalized);
      runtimeObjectStorage.replace(next);
      redis.delete(RedisKeys.setting(SETTING_NAME));
      log.info("system oss switched to provider={}", next.providerId());
      return maskSecrets(normalized);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
  }

  @Override
  public void assertAllowed(String originalFilename) {
    OssSettingsDocument doc = OssSettingsSupport.normalize(loadEffective(), false);
    OssSettingsSupport.assertExtensionAllowed(doc.allowExtensions(), originalFilename);
  }

  /** 当前生效的存储引擎 id（供上传库登记；无需管理员）。 */
  public String currentProviderId() {
    OssSettingsDocument doc = OssSettingsSupport.normalize(loadEffective(), false);
    String p = doc.provider();
    return p == null || p.isBlank() ? "local" : p.trim().toLowerCase();
  }

  /**
   * 管理员连通性检测：对当前引擎 put 探针对象后 delete。
   *
   * @return {@code ok} · {@code provider} · {@code key} · {@code url}
   */
  public Map<String, Object> check() {
    adminGuard.requireAdmin();
    String key = "media/oss-check/" + UUID.randomUUID().toString().replace("-", "") + ".txt";
    byte[] payload = ("bluedock-oss-check " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
    String url;
    try {
      url =
          runtimeObjectStorage.put(
              key, new ByteArrayInputStream(payload), payload.length, "text/plain");
      runtimeObjectStorage.delete(key);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("oss check failed provider={}: {}", runtimeObjectStorage.providerId(), e.toString());
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_CHECK_FAILED);
    }
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("provider", runtimeObjectStorage.providerId());
    out.put("key", key);
    out.put("url", url == null ? "" : url);
    return out;
  }

  /** 启动时若 DB 有配置则覆盖 yaml 默认。 */
  public void applyStoredOnStartup() {
    OssSettingsDocument stored = loadFromDb();
    if (stored == null) {
      return;
    }
    try {
      OssSettingsDocument normalized = OssSettingsSupport.normalize(stored, false);
      ObjectStorage next =
          OssStorageFactory.create(
              toProperties(normalized), nullToEmpty(bootOssProperties.getPublicBaseUrl()));
      runtimeObjectStorage.replace(next);
      log.info("system oss loaded from DB provider={}", next.providerId());
    } catch (Exception ex) {
      log.warn("system oss DB config invalid, keep boot defaults: {}", ex.getMessage());
    }
  }

  private OssSettingsDocument loadEffective() {
    OssSettingsDocument stored = loadFromDb();
    return stored != null ? stored : fromBootProperties();
  }

  private OssSettingsDocument loadFromDb() {
    return settings
        .findSettingJson(SETTING_NAME)
        .map(
            json -> {
              try {
                return objectMapper.readValue(json, OssSettingsDocument.class);
              } catch (JacksonException e) {
                log.warn("invalid oss settings json: {}", e.getMessage());
                return null;
              }
            })
        .orElse(null);
  }

  private void persist(OssSettingsDocument doc) {
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(doc));
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_SAVE_FAILED);
    }
  }

  private OssSettingsDocument fromBootProperties() {
    OssProperties p = bootOssProperties;
    String publicBaseUrl = nullToEmpty(p.getPublicBaseUrl());
    OssSettingsSupport.Split split = OssSettingsSupport.splitPublicBaseUrl(publicBaseUrl);
    return new OssSettingsDocument(
        p.getProvider(),
        OssSettingsDocument.NAME_DATE_RANDOM,
        OssSettingsDocument.LINK_SIMPLE,
        OssSettingsDocument.DEFAULT_ALLOW_EXTENSIONS,
        split != null ? split.protocol() : OssSettingsDocument.PROTO_HTTPS,
        split != null ? split.domain() : "",
        publicBaseUrl,
        new OssSettingsDocument.Local(p.getLocal().getStoragePath()),
        new OssSettingsDocument.Huawei(
            p.getHuawei().getEndpoint(),
            p.getHuawei().getAccessKey(),
            p.getHuawei().getSecretKey(),
            p.getHuawei().getBucket()),
        new OssSettingsDocument.Aliyun(
            p.getAliyun().getEndpoint(),
            p.getAliyun().getAccessKeyId(),
            p.getAliyun().getAccessKeySecret(),
            p.getAliyun().getBucket()),
        new OssSettingsDocument.Tencent(
            p.getTencent().getRegion(),
            p.getTencent().getSecretId(),
            p.getTencent().getSecretKey(),
            p.getTencent().getBucket()),
        new OssSettingsDocument.Qiniu(
            p.getQiniu().getAccessKey(),
            p.getQiniu().getSecretKey(),
            p.getQiniu().getBucket(),
            p.getQiniu().getRegion()));
  }

  static OssProperties toProperties(OssSettingsDocument doc) {
    OssProperties p = new OssProperties();
    p.setProvider(doc.provider());
    p.setPublicBaseUrl(nullToEmpty(doc.publicBaseUrl()));
    if (doc.local() != null) {
      p.getLocal().setStoragePath(nullToEmpty(doc.local().storagePath()));
    }
    if (doc.huawei() != null) {
      p.getHuawei().setEndpoint(nullToEmpty(doc.huawei().endpoint()));
      p.getHuawei().setAccessKey(nullToEmpty(doc.huawei().accessKey()));
      p.getHuawei().setSecretKey(nullToEmpty(doc.huawei().secretKey()));
      p.getHuawei().setBucket(nullToEmpty(doc.huawei().bucket()));
    }
    if (doc.aliyun() != null) {
      p.getAliyun().setEndpoint(nullToEmpty(doc.aliyun().endpoint()));
      p.getAliyun().setAccessKeyId(nullToEmpty(doc.aliyun().accessKeyId()));
      p.getAliyun().setAccessKeySecret(nullToEmpty(doc.aliyun().accessKeySecret()));
      p.getAliyun().setBucket(nullToEmpty(doc.aliyun().bucket()));
    }
    if (doc.tencent() != null) {
      p.getTencent().setRegion(nullToEmpty(doc.tencent().region()));
      p.getTencent().setSecretId(nullToEmpty(doc.tencent().secretId()));
      p.getTencent().setSecretKey(nullToEmpty(doc.tencent().secretKey()));
      p.getTencent().setBucket(nullToEmpty(doc.tencent().bucket()));
    }
    if (doc.qiniu() != null) {
      p.getQiniu().setAccessKey(nullToEmpty(doc.qiniu().accessKey()));
      p.getQiniu().setSecretKey(nullToEmpty(doc.qiniu().secretKey()));
      p.getQiniu().setBucket(nullToEmpty(doc.qiniu().bucket()));
      p.getQiniu().setRegion(nullToEmpty(doc.qiniu().region()));
    }
    p.setProvider(OssProvider.fromConfig(doc.provider()).name().toLowerCase());
    return p;
  }

  public static OssSettingsDocument mergeSecrets(
      OssSettingsDocument existing, OssSettingsDocument incoming) {
    return new OssSettingsDocument(
        incoming.provider(),
        firstNonBlank(incoming.nameType(), existing.nameType()),
        firstNonBlank(incoming.linkType(), existing.linkType()),
        firstNonBlank(incoming.allowExtensions(), existing.allowExtensions()),
        firstNonBlank(incoming.protocol(), existing.protocol()),
        incoming.domain() != null ? incoming.domain() : nullToEmpty(existing.domain()),
        firstNonBlank(incoming.publicBaseUrl(), existing.publicBaseUrl()),
        incoming.local() != null
            ? incoming.local()
            : existing.local() != null
                ? existing.local()
                : new OssSettingsDocument.Local("./data/uploads"),
        mergeHuawei(existing.huawei(), incoming.huawei()),
        mergeAliyun(existing.aliyun(), incoming.aliyun()),
        mergeTencent(existing.tencent(), incoming.tencent()),
        mergeQiniu(existing.qiniu(), incoming.qiniu()));
  }

  private static OssSettingsDocument.Huawei mergeHuawei(
      OssSettingsDocument.Huawei oldV, OssSettingsDocument.Huawei in) {
    if (in == null) {
      return oldV;
    }
    OssSettingsDocument.Huawei base =
        oldV != null ? oldV : new OssSettingsDocument.Huawei("", "", "", "");
    return new OssSettingsDocument.Huawei(
        nullToEmpty(in.endpoint()),
        keepSecret(in.accessKey(), base.accessKey()),
        keepSecret(in.secretKey(), base.secretKey()),
        nullToEmpty(in.bucket()));
  }

  private static OssSettingsDocument.Aliyun mergeAliyun(
      OssSettingsDocument.Aliyun oldV, OssSettingsDocument.Aliyun in) {
    if (in == null) {
      return oldV;
    }
    OssSettingsDocument.Aliyun base =
        oldV != null ? oldV : new OssSettingsDocument.Aliyun("", "", "", "");
    return new OssSettingsDocument.Aliyun(
        nullToEmpty(in.endpoint()),
        keepSecret(in.accessKeyId(), base.accessKeyId()),
        keepSecret(in.accessKeySecret(), base.accessKeySecret()),
        nullToEmpty(in.bucket()));
  }

  private static OssSettingsDocument.Tencent mergeTencent(
      OssSettingsDocument.Tencent oldV, OssSettingsDocument.Tencent in) {
    if (in == null) {
      return oldV;
    }
    OssSettingsDocument.Tencent base =
        oldV != null ? oldV : new OssSettingsDocument.Tencent("", "", "", "");
    return new OssSettingsDocument.Tencent(
        nullToEmpty(in.region()),
        keepSecret(in.secretId(), base.secretId()),
        keepSecret(in.secretKey(), base.secretKey()),
        nullToEmpty(in.bucket()));
  }

  private static OssSettingsDocument.Qiniu mergeQiniu(
      OssSettingsDocument.Qiniu oldV, OssSettingsDocument.Qiniu in) {
    if (in == null) {
      return oldV;
    }
    OssSettingsDocument.Qiniu base =
        oldV != null ? oldV : new OssSettingsDocument.Qiniu("", "", "", "z0");
    return new OssSettingsDocument.Qiniu(
        keepSecret(in.accessKey(), base.accessKey()),
        keepSecret(in.secretKey(), base.secretKey()),
        nullToEmpty(in.bucket()),
        StringUtils.hasText(in.region()) ? in.region() : base.region());
  }

  public static OssSettingsDocument maskSecrets(OssSettingsDocument doc) {
    return new OssSettingsDocument(
        doc.provider(),
        doc.nameType(),
        doc.linkType(),
        doc.allowExtensions(),
        doc.protocol(),
        doc.domain(),
        doc.publicBaseUrl(),
        doc.local(),
        doc.huawei() == null
            ? null
            : new OssSettingsDocument.Huawei(
                doc.huawei().endpoint(),
                maskIfPresent(doc.huawei().accessKey()),
                maskIfPresent(doc.huawei().secretKey()),
                doc.huawei().bucket()),
        doc.aliyun() == null
            ? null
            : new OssSettingsDocument.Aliyun(
                doc.aliyun().endpoint(),
                maskIfPresent(doc.aliyun().accessKeyId()),
                maskIfPresent(doc.aliyun().accessKeySecret()),
                doc.aliyun().bucket()),
        doc.tencent() == null
            ? null
            : new OssSettingsDocument.Tencent(
                doc.tencent().region(),
                maskIfPresent(doc.tencent().secretId()),
                maskIfPresent(doc.tencent().secretKey()),
                doc.tencent().bucket()),
        doc.qiniu() == null
            ? null
            : new OssSettingsDocument.Qiniu(
                maskIfPresent(doc.qiniu().accessKey()),
                maskIfPresent(doc.qiniu().secretKey()),
                doc.qiniu().bucket(),
                doc.qiniu().region()));
  }

  private static String keepSecret(String incoming, String existing) {
    if (!StringUtils.hasText(incoming) || OssSettingsDocument.MASK.equals(incoming)) {
      return nullToEmpty(existing);
    }
    return incoming;
  }

  private static String maskIfPresent(String value) {
    return StringUtils.hasText(value) ? OssSettingsDocument.MASK : "";
  }

  private static String firstNonBlank(String preferred, String fallback) {
    return StringUtils.hasText(preferred) ? preferred : nullToEmpty(fallback);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
