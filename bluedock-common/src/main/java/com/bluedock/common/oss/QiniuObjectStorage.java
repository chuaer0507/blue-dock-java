package com.bluedock.common.oss;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import java.io.InputStream;
import org.springframework.util.StringUtils;

/** 七牛云 Kodo。 */
public final class QiniuObjectStorage implements ObjectStorage {

  private final OssProperties properties;

  public QiniuObjectStorage(OssProperties properties) {
    this.properties = properties;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Qiniu cfg = properties.getQiniu();
    Auth auth = Auth.create(cfg.getAccessKey(), cfg.getSecretKey());
    String token = auth.uploadToken(cfg.getBucket());
    Configuration configuration = Configuration.create(resolveRegion(cfg.getRegion()));
    UploadManager uploadManager = new UploadManager(configuration);
    try {
      long len = contentLength >= 0 ? contentLength : -1;
      String mime = StringUtils.hasText(contentType) ? contentType : null;
      Response response = uploadManager.put(content, len, normalized, token, null, mime, false);
      if (!response.isOK()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_UPLOAD_FAILED);
    }
    return properties.buildPublicUrl(normalized);
  }

  @Override
  public void delete(String key) {
    String normalized = LocalObjectStorage.normalizeKey(key);
    OssProperties.Qiniu cfg = properties.getQiniu();
    Auth auth = Auth.create(cfg.getAccessKey(), cfg.getSecretKey());
    Configuration configuration = Configuration.create(resolveRegion(cfg.getRegion()));
    com.qiniu.storage.BucketManager bucketManager = new com.qiniu.storage.BucketManager(auth, configuration);
    try {
      Response response = bucketManager.delete(cfg.getBucket(), normalized);
      // 612 = no such file → 视为成功
      if (!response.isOK() && response.statusCode != 612) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DELETE_FAILED);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_DELETE_FAILED);
    }
  }

  @Override
  public String providerId() {
    return "qiniu";
  }

  static Region resolveRegion(String code) {
    String regionId =
        (code == null || code.isBlank())
            ? "z0"
            : switch (code.trim().toLowerCase()) {
              case "z1", "huabei" -> "z1";
              case "z2", "huanan" -> "z2";
              case "na0", "northamerica" -> "na0";
              case "as0", "singapore" -> "as0";
              case "z0", "huadong" -> "z0";
              default -> "z0";
            };
    return Region.createWithRegionId(regionId);
  }
}
