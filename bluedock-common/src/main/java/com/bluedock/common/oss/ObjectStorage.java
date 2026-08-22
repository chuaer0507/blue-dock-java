package com.bluedock.common.oss;

import java.io.InputStream;

/** 统一对象存储：本地 {@code public/{type}/...} 或云厂商 bucket。 */
public interface ObjectStorage {

  /**
   * 上传对象。
   *
   * @param key 对象键，须含类型前缀，如 {@code releases/...}、{@code media/...}
   * @param content 内容流（调用方负责关闭时可在 put 内读完后关闭）
   * @param contentLength 字节长度；未知时可传 -1（部分实现不支持）
   * @param contentType MIME，可空
   * @return 公开访问 URL
   */
  String put(String key, InputStream content, long contentLength, String contentType);

  /** 删除对象（本地删文件 / 云厂商 deleteObject）。键不存在时视为成功。 */
  void delete(String key);

  /**
   * 打开对象流；调用方负责关闭。默认不支持（云厂商可覆写或走预签名 URL）。
   *
   * @throws com.bluedock.common.exception.BusinessException 对象不存在或当前后端不支持直读
   */
  default InputStream open(String key) {
    throw new com.bluedock.common.exception.BusinessException(
        com.bluedock.common.exception.ErrorCodes.NOT_FOUND,
        com.bluedock.common.i18n.I18nKeys.FILE_CONTENT_NOT_FOUND);
  }

  /** 当前是否为本地落盘（用于注册静态资源映射）。 */
  default boolean isLocal() {
    return false;
  }

  /** 当前存储提供方标识。 */
  default String providerId() {
    return "local";
  }
}
