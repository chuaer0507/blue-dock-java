package com.bluedock.auth.ldap;

/** LDAP 认证成功后的本地合并信息。 */
public record LdapUserInfo(String email, String nickname, String dn) {}
