package com.bluedock.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultModel<T>(int code, String message, T data) {

  public static <T> ResultModel<T> ok(T data) {
    return new ResultModel<>(0, "", data);
  }

  public static <T> ResultModel<T> ok() {
    return ok(null);
  }

  public static <T> ResultModel<T> fail(int code, String message) {
    return new ResultModel<>(code, message, null);
  }
}
