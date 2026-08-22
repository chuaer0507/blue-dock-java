package com.bluedock.assistant.web;

import com.bluedock.assistant.service.AiInvokeStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 助手流式推理入口。鉴权靠一次性 {@code streamKey}（见 {@code POST /api/assistant/auth}），无需 Bearer。
 */
@RestController
@RequestMapping("/api/ai/invoke")
public class AiInvokeController {
  private final AiInvokeStreamService invoke;

  public AiInvokeController(AiInvokeStreamService invoke) {
    this.invoke = invoke;
  }

  @GetMapping(value = "/stream/{streamKey}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String streamKey) {
    return invoke.stream(streamKey);
  }
}
