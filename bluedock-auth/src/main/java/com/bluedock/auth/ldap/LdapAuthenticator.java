package com.bluedock.auth.ldap;

import java.util.Optional;

/**
 * LDAP 按需认证 / 反向同步端口；由 {@code bluedock-system} 提供实现，避免 auth→system 循环依赖。
 */
public interface LdapAuthenticator {
  boolean isEnabled();

  /**
   * 用登录名（邮箱或 loginAttr）+ 密码做目录认证。
   *
   * @return 成功时含邮箱与昵称；失败 empty（不抛业务异常，由 AuthService 统一 AUTH_FAILED）
   */
  Optional<LdapUserInfo> authenticate(String login, String password);

  /**
   * 本地账号反向写入目录（仅 {@code ldapSyncLocal=open}）。
   *
   * <p>目录已存在同邮箱条目时返回 {@code false}；新建成功返回 {@code true}（调用方应打 {@code
   * ldap} identity）。失败静默返回 {@code false}。
   */
  boolean syncLocalUser(String email, String nickname, String plainPassword);

  /**
   * 将本地改密回写到目录 {@code userPassword}。
   *
   * <p>LDAP 未开启、找不到条目或写失败时返回 {@code false}；成功返回 {@code true}。
   */
  boolean updatePassword(String email, String newPlainPassword);
}
