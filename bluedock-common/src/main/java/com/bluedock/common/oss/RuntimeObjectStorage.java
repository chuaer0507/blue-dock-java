package com.bluedock.common.oss;

import java.io.InputStream;
import java.util.Objects;

/** 可热切换的 ObjectStorage 包装（Admin 保存配置后 replace）。 */
public final class RuntimeObjectStorage implements ObjectStorage {

  private volatile ObjectStorage delegate;

  public RuntimeObjectStorage(ObjectStorage initial) {
    this.delegate = Objects.requireNonNull(initial, "initial");
  }

  public void replace(ObjectStorage next) {
    this.delegate = Objects.requireNonNull(next, "next");
  }

  public ObjectStorage current() {
    return delegate;
  }

  @Override
  public String put(String key, InputStream content, long contentLength, String contentType) {
    return delegate.put(key, content, contentLength, contentType);
  }

  @Override
  public void delete(String key) {
    delegate.delete(key);
  }

  @Override
  public InputStream open(String key) {
    return delegate.open(key);
  }

  @Override
  public boolean isLocal() {
    return delegate.isLocal();
  }

  @Override
  public String providerId() {
    return delegate.providerId();
  }
}
