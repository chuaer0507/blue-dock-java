package com.bluedock.report.domain;

import java.time.LocalDateTime;

public class Report {
  private long id;
  private String sign;
  private String title;
  private String type;
  private long userId;
  private String content;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer read;
  private LocalDateTime receiveAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getSign() {
    return sign;
  }

  public void setSign(String sign) {
    this.sign = sign;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Integer getRead() {
    return read;
  }

  public void setRead(Integer read) {
    this.read = read;
  }

  public LocalDateTime getReceiveAt() {
    return receiveAt;
  }

  public void setReceiveAt(LocalDateTime receiveAt) {
    this.receiveAt = receiveAt;
  }
}
