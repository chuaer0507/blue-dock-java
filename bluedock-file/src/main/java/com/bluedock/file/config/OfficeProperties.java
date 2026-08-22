package com.bluedock.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.office")
public class OfficeProperties {
  /** 未启用时 office/token 拒绝。 */
  private boolean enabled = false;
  /** OnlyOffice Document Server 地址，如 http://127.0.0.1:8082 */
  private String documentServerUrl = "";
  /** HS256 JWT 密钥；空则仅发 Redis 会话 token */
  private String jwtSecret = "";
  /** 文档/回调对 Document Server 可达的公网或内网基址 */
  private String publicBaseUrl = "http://127.0.0.1:18080";
  /** 开发态允许未配 Document Server 仍签发 token */
  private boolean allowDevToken = true;
  private long tokenTtlSeconds = 7200L;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDocumentServerUrl() {
    return documentServerUrl;
  }

  public void setDocumentServerUrl(String documentServerUrl) {
    this.documentServerUrl = documentServerUrl;
  }

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public boolean isAllowDevToken() {
    return allowDevToken;
  }

  public void setAllowDevToken(boolean allowDevToken) {
    this.allowDevToken = allowDevToken;
  }

  public long getTokenTtlSeconds() {
    return tokenTtlSeconds;
  }

  public void setTokenTtlSeconds(long tokenTtlSeconds) {
    this.tokenTtlSeconds = tokenTtlSeconds;
  }
}
