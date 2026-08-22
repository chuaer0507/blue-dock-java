package com.bluedock.auth.domain;

import java.time.LocalDateTime;

/** 用户行（不含 password 的出站 DTO 见 LoginResult / UserPublicView）。 */
public class UserAccount {
  private long userId;
  private String identity;
  private String nameAz;
  private String email;
  private String nickname;
  private String userImage;
  private String profession;
  private String telephone;
  private String birthday;
  private String address;
  private String introduction;
  private String lang;
  private String password;
  private int isBot;
  private int emailVerify;
  private Integer mustChangePassword;
  private LocalDateTime disableAt;

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getIdentity() {
    return identity;
  }

  public void setIdentity(String identity) {
    this.identity = identity;
  }

  public String getNameAz() {
    return nameAz;
  }

  public void setNameAz(String nameAz) {
    this.nameAz = nameAz;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getUserImage() {
    return userImage;
  }

  public void setUserImage(String userImage) {
    this.userImage = userImage;
  }

  public String getProfession() {
    return profession;
  }

  public void setProfession(String profession) {
    this.profession = profession;
  }

  public String getTelephone() {
    return telephone;
  }

  public void setTelephone(String telephone) {
    this.telephone = telephone;
  }

  public String getBirthday() {
    return birthday;
  }

  public void setBirthday(String birthday) {
    this.birthday = birthday;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getIntroduction() {
    return introduction;
  }

  public void setIntroduction(String introduction) {
    this.introduction = introduction;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public int getIsBot() {
    return isBot;
  }

  public void setIsBot(int isBot) {
    this.isBot = isBot;
  }

  public int getEmailVerify() {
    return emailVerify;
  }

  public void setEmailVerify(int emailVerify) {
    this.emailVerify = emailVerify;
  }

  public Integer getMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(Integer mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }

  public LocalDateTime getDisableAt() {
    return disableAt;
  }

  public void setDisableAt(LocalDateTime disableAt) {
    this.disableAt = disableAt;
  }
}
