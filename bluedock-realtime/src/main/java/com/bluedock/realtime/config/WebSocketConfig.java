package com.bluedock.realtime.config;

import com.bluedock.realtime.ws.AuthHandshakeInterceptor;
import com.bluedock.realtime.ws.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final RealtimeWebSocketHandler handler;
  private final AuthHandshakeInterceptor authInterceptor;

  public WebSocketConfig(
      RealtimeWebSocketHandler handler, AuthHandshakeInterceptor authInterceptor) {
    this.handler = handler;
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws").addInterceptors(authInterceptor).setAllowedOrigins("*");
  }
}
