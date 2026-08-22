package com.bluedock.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 演示帐号与更新日志路径。 */
@ConfigurationProperties(prefix = "bluedock")
public class BlueDockPublicProperties {
  private final Demo demo = new Demo();
  private final Changelog changelog = new Changelog();

  public Demo getDemo() {
    return demo;
  }

  public Changelog getChangelog() {
    return changelog;
  }

  public static class Demo {
    /** 演示登录帐号（邮箱或用户名）；空则接口返回未配置。 */
    private String account = "";
    /** 演示登录明文密码（仅此公开接口回显；空则未配置）。 */
    private String password = "";

    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account == null ? "" : account;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password == null ? "" : password;
    }
  }

  public static class Changelog {
    /** CHANGELOG.md 路径；相对进程工作目录或绝对路径。 */
    private String path = "CHANGELOG.md";

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path == null || path.isBlank() ? "CHANGELOG.md" : path.trim();
    }
  }
}
