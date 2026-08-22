package com.bluedock.common.auth;

/** 注册 / 邮箱验证策略（由 bluedock-system 实现）。 */
public interface RegPolicy {
  /** {@code systemSetting.reg == invite} 时注册页需邀请码。 */
  boolean needInvite();

  /** {@code systemSetting.reg == close} 时禁止自助注册。 */
  boolean isRegistrationClosed();

  /**
   * invite 模式下校验邀请码；非 invite 模式直接通过。
   *
   * @throws com.bluedock.common.exception.BusinessException 邀请码错误
   */
  void assertInvite(String invite);

  /** {@code emailSetting.regVerify == open} 时未验证邮箱禁止登录。 */
  boolean isRegVerifyOpen();
}
