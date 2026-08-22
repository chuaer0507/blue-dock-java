package com.bluedock.common.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.io.InputStream;
import org.springframework.util.StringUtils;

/** 阿里云 OSS。 */
public final class AliyunObjectStorage implements ObjectStorage {

  private final OssProperties properties;

  public AliyunObjectStorage(OssProperties properties) {
    this.properties = properties;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Aliyun cfg = properties.getAliyun();
    OSS client =
        new OSSClientBuilder()
            .build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
    try {
      ObjectMetadata meta = new ObjectMetadata();
      if (contentLength >= 0) {
        meta.setContentLength(contentLength);
      }
      if (StringUtils.hasText(contentType)) {
        meta.setContentType(contentType);
      }
      client.putObject(cfg.getBucket(), normalized, content, meta);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
    } finally {
      client.shutdown();
    }
    return properties.buildPublicUrl(normalized);
  }

  @Override
  public void delete(String key) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Aliyun cfg = properties.getAliyun();
    OSS client =
        new OSSClientBuilder()
            .build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
    try {
      client.deleteObject(cfg.getBucket(), normalized);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DELETE_FAILED);
    } finally {
      client.shutdown();
    }
  }

  @Override
  public String providerId() {
    return "aliyun";
  }
}
