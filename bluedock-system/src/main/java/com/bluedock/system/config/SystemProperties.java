package com.bluedock.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.system")
public class SystemProperties {
  private String licensePath = "./data/secrets/license.json";
  /** 覆盖自动探测的机器 SN；空则按 hostname+MAC 生成。 */
  private String machineSn = "";
  /**
   * 在线授权模式：{@code local} 本机模拟（默认）；{@code remote} 调官方商店 HTTP。
   */
  private String licenseOnlineMode = "local";
  /** 官方在线授权 Base URL；remote 模式必填。 */
  private String licenseOnlineUrl = "";
  /** 本机试用天数上限（硬上限 60）。 */
  private int licenseTrialDays = 14;
  /** 本机试用席位数（1–3 小团队档）。 */
  private int licenseTrialPeople = 3;
  /**
   * MaxMind GeoLite2/GeoIP2 Country MMDB 路径；空则仅用 CDN 国家头 + 内网启发式。
   */
  private String geoipMmdb = "";

  public String getLicensePath() {
    return licensePath;
  }

  public void setLicensePath(String licensePath) {
    this.licensePath = licensePath;
  }

  public String getMachineSn() {
    return machineSn;
  }

  public void setMachineSn(String machineSn) {
    this.machineSn = machineSn;
  }

  public String getLicenseOnlineMode() {
    return licenseOnlineMode;
  }

  public void setLicenseOnlineMode(String licenseOnlineMode) {
    this.licenseOnlineMode = licenseOnlineMode;
  }

  public String getLicenseOnlineUrl() {
    return licenseOnlineUrl;
  }

  public void setLicenseOnlineUrl(String licenseOnlineUrl) {
    this.licenseOnlineUrl = licenseOnlineUrl;
  }

  public int getLicenseTrialDays() {
    return licenseTrialDays;
  }

  public void setLicenseTrialDays(int licenseTrialDays) {
    this.licenseTrialDays = licenseTrialDays;
  }

  public int getLicenseTrialPeople() {
    return licenseTrialPeople;
  }

  public void setLicenseTrialPeople(int licenseTrialPeople) {
    this.licenseTrialPeople = licenseTrialPeople;
  }

  public String getGeoipMmdb() {
    return geoipMmdb;
  }

  public void setGeoipMmdb(String geoipMmdb) {
    this.geoipMmdb = geoipMmdb;
  }
}
