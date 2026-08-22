package com.bluedock.common.oss;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.PutObjectRequest;
import java.io.InputStream;
import org.springframework.util.StringUtils;

/** 华为云 OBS。 */
public final class HuaweiObjectStorage implements ObjectStorage {

  private final OssProperties properties;

  public HuaweiObjectStorage(OssProperties properties) {
    this.properties = properties;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Huawei cfg = properties.getHuawei();
    try (ObsClient client =
        new ObsClient(cfg.getAccessKey(), cfg.getSecretKey(), cfg.getEndpoint())) {
      PutObjectRequest request = new PutObjectRequest();
      request.setBucketName(cfg.getBucket());
      request.setObjectKey(normalized);
      request.setInput(content);
      if (contentLength >= 0) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(contentLength);
        if (StringUtils.hasText(contentType)) {
          meta.setContentType(contentType);
        }
        request.setMetadata(meta);
      } else if (StringUtils.hasText(contentType)) {
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentType(contentType);
        request.setMetadata(meta);
      }
      client.putObject(request);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
    }
    return properties.buildPublicUrl(normalized);
  }

  @Override
  public void delete(String key) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Huawei cfg = properties.getHuawei();
    try (ObsClient client =
        new ObsClient(cfg.getAccessKey(), cfg.getSecretKey(), cfg.getEndpoint())) {
      client.deleteObject(cfg.getBucket(), normalized);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DELETE_FAILED);
    }
  }

  @Override
  public String providerId() {
    return "huawei";
  }
}
