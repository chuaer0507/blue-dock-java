package com.bluedock.common.notify.apppush;

/** 友盟 customizedcast 请求/响应（供日志落库）。 */
public record AppPushSendResult(String requestBody, String responseBody) {}
