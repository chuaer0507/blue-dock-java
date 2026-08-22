package com.bluedock.common.bot;

public interface UserBotWebhookPublisher {
  void publish(UserBotWebhookEvent event);
}
