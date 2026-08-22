package com.bluedock.auth.domain;

import java.time.LocalDateTime;

public class AuthKeypair {
  private String id;
  private String keyId;
  private String publicKey;
  private String privateKeyEnc;
  private String algorithm;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime expiredAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public String getPrivateKeyEnc() {
    return privateKeyEnc;
  }

  public void setPrivateKeyEnc(String privateKeyEnc) {
    this.privateKeyEnc = privateKeyEnc;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getExpiredAt() {
    return expiredAt;
  }

  public void setExpiredAt(LocalDateTime expiredAt) {
    this.expiredAt = expiredAt;
  }
}
