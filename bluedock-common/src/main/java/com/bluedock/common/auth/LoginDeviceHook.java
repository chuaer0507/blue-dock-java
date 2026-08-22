package com.bluedock.common.auth;

/** 登录成功后记录设备会话；由 user 模块实现，auth 可选注入。 */
public interface LoginDeviceHook {
  void onLogin(long userId, String token, String userAgent, String clientIp);
}
