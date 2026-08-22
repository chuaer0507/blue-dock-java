package com.bluedock.common.oss;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import java.io.InputStream;
import org.springframework.util.StringUtils;

/** 腾讯云 COS。 */
public final class TencentObjectStorage implements ObjectStorage {

  private final OssProperties properties;

  public TencentObjectStorage(OssProperties properties) {
    this.properties = properties;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Tencent cfg = properties.getTencent();
    COSCredentials cred = new BasicCOSCredentials(cfg.getSecretId(), cfg.getSecretKey());
    ClientConfig clientConfig = new ClientConfig(new Region(cfg.getRegion()));
    COSClient client = new COSClient(cred, clientConfig);
    try {
      ObjectMetadata meta = new ObjectMetadata();
      if (contentLength >= 0) {
        meta.setContentLength(contentLength);
      }
      if (StringUtils.hasText(contentType)) {
        meta.setContentType(contentType);
      }
      PutObjectRequest request =
          new PutObjectRequest(cfg.getBucket(), normalized, content, meta);
      client.putObject(request);
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
    OssProperties.Tencent cfg = properties.getTencent();
    COSCredentials cred = new BasicCOSCredentials(cfg.getSecretId(), cfg.getSecretKey());
    ClientConfig clientConfig = new ClientConfig(new Region(cfg.getRegion()));
    COSClient client = new COSClient(cred, clientConfig);
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
    return "tencent";
  }
}
