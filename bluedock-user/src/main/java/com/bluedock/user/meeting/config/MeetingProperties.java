package com.bluedock.user.meeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.meeting")
public class MeetingProperties {
  private boolean enabled = true;
  private String appId = "";
  private String appCertificate = "";
  private boolean allowDevToken = true;
  private String channelSalt = "dev";
  private String shareBaseUrl = "http://localhost:5173";
  private int shareTtlHours = 6;
  /** Agora RESTful（关房查频道）；可选 */
  private String apiKey = "";
  private String apiSecret = "";
  /** 无 REST 凭证时是否按「久未更新」直接关房（仅开发） */
  private boolean allowCloseWithoutRest = false;
  private int closeIdleMinutes = 10;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getAppCertificate() {
    return appCertificate;
  }

  public void setAppCertificate(String appCertificate) {
    this.appCertificate = appCertificate;
  }

  public boolean isAllowDevToken() {
    return allowDevToken;
  }

  public void setAllowDevToken(boolean allowDevToken) {
    this.allowDevToken = allowDevToken;
  }

  public String getChannelSalt() {
    return channelSalt;
  }

  public void setChannelSalt(String channelSalt) {
    this.channelSalt = channelSalt;
  }

  public String getShareBaseUrl() {
    return shareBaseUrl;
  }

  public void setShareBaseUrl(String shareBaseUrl) {
    this.shareBaseUrl = shareBaseUrl;
  }

  public int getShareTtlHours() {
    return shareTtlHours;
  }

  public void setShareTtlHours(int shareTtlHours) {
    this.shareTtlHours = shareTtlHours;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public boolean isAllowCloseWithoutRest() {
    return allowCloseWithoutRest;
  }

  public void setAllowCloseWithoutRest(boolean allowCloseWithoutRest) {
    this.allowCloseWithoutRest = allowCloseWithoutRest;
  }

  public int getCloseIdleMinutes() {
    return closeIdleMinutes;
  }

  public void setCloseIdleMinutes(int closeIdleMinutes) {
    this.closeIdleMinutes = closeIdleMinutes;
  }
}
