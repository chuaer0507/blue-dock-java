package com.bluedock.common.license;

/** 用户扩容 License 守卫；由 bluedock-system 提供实现。 */
public interface LicenseCapacity {
  /** 再增加一名非机器人用户前调用；超额或过期抛业务异常。 */
  void assertCanAddUser();
}
