package com.bluedock.common.attendance;

/**
 * 人脸签到识别；由 face 插件实现。
 *
 * <p>无 Bean 或 {@link #available()} 为 false 时，主程序拒绝人脸上传与刷脸打卡。
 */
public interface AttendanceFaceBridge {

  /** face 插件是否可用。 */
  boolean available();

  /**
   * 校验现场抓拍是否与用户已存人脸匹配。
   *
   * @param userId 用户 ID
   * @param enrolledUploadObjectId 已登记人脸对应的上传对象 ID
   * @param captureUploadObjectId 现场抓拍上传对象 ID
   */
  boolean match(long userId, long enrolledUploadObjectId, long captureUploadObjectId);
}
