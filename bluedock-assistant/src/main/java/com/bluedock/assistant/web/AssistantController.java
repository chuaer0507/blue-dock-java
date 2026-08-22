package com.bluedock.assistant.web;

import com.bluedock.assistant.service.AssistantService;
import com.bluedock.assistant.web.dto.AssistantSessionView;
import com.bluedock.common.model.ResultModel;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
  private final AssistantService assistant;

  public AssistantController(AssistantService assistant) {
    this.assistant = assistant;
  }

  @PostMapping("/auth")
  public ResultModel<Map<String, Object>> auth(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        assistant.auth(
            str(b.get("modelType")),
            str(b.get("modelName")),
            b.get("context"),
            str(b.get("locale")),
            str(b.get("sessionId")),
            str(b.get("fd"))));
  }

  @GetMapping("/models")
  public ResultModel<Map<String, Object>> models() {
    return ResultModel.ok(assistant.models());
  }

  @PostMapping("/matchElements")
  public ResultModel<Map<String, Object>> matchElements(
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> elements =
        b.get("elements") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    Integer topK = b.get("topK") instanceof Number n ? n.intValue() : null;
    return ResultModel.ok(assistant.matchElements(str(b.get("query")), elements, topK));
  }

  @PostMapping("/log/search")
  public ResultModel<Void> logSearch(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    @SuppressWarnings("unchecked")
    List<Object> sourceIds =
        b.get("sourceIds") instanceof List<?> list ? (List<Object>) list : List.of();
    Long dialogId = b.get("dialogId") instanceof Number n ? n.longValue() : null;
    Double topScore = b.get("topScore") instanceof Number n ? n.doubleValue() : null;
    Integer resultCount = b.get("resultCount") instanceof Number n ? n.intValue() : null;
    Integer durationMs = b.get("durationMs") instanceof Number n ? n.intValue() : null;
    assistant.logSearch(
        str(b.get("query")),
        str(b.get("locale")),
        str(b.get("source")),
        str(b.get("contextKey")),
        dialogId,
        sourceIds,
        topScore,
        resultCount,
        durationMs);
    return ResultModel.ok();
  }

  @PostMapping("/feedback/save")
  public ResultModel<Map<String, Object>> feedbackSave(
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    Long localId = b.get("localId") instanceof Number n ? n.longValue() : null;
    @SuppressWarnings("unchecked")
    List<Object> sourceIds =
        b.get("sourceIds") instanceof List<?> list ? (List<Object>) list : List.of();
    return ResultModel.ok(
        assistant.feedbackSave(
            str(b.get("sessionKey")),
            str(b.get("sessionId")),
            localId,
            str(b.get("feedback")),
            str(b.get("prompt")),
            str(b.get("answer")),
            sourceIds,
            str(b.get("model"))));
  }

  @PostMapping("/operation/dispatch")
  public ResultModel<Map<String, Object>> operationDispatch(
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        assistant.operationDispatch(str(b.get("fd")), str(b.get("action")), b.get("payload")));
  }

  @GetMapping("/operation/result")
  public ResultModel<Map<String, Object>> operationResult(
      @RequestParam(required = false) String requestId) {
    return ResultModel.ok(assistant.operationResult(requestId));
  }

  @RequestMapping(
      value = "/session/list",
      method = {RequestMethod.GET, RequestMethod.POST})
  public ResultModel<List<AssistantSessionView>> sessionList(
      @RequestParam(required = false) String sessionKey,
      @RequestBody(required = false) Map<String, Object> body) {
    String key = sessionKey;
    if ((key == null || key.isBlank()) && body != null) {
      key = str(body.get("sessionKey"));
    }
    return ResultModel.ok(assistant.sessionList(key));
  }

  @RequestMapping(
      value = "/session/save",
      method = {RequestMethod.GET, RequestMethod.POST})
  public ResultModel<Map<String, Object>> sessionSave(
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        assistant.sessionSave(
            str(b.get("sessionKey")),
            str(b.get("sessionId")),
            str(b.get("sceneKey")),
            str(b.get("title")),
            b.get("data"),
            b.containsKey("newImages") ? b.get("newImages") : b.get("new_images")));
  }

  @RequestMapping(
      value = "/session/delete",
      method = {RequestMethod.GET, RequestMethod.POST})
  public ResultModel<Void> sessionDelete(
      @RequestParam(required = false) String sessionKey,
      @RequestParam(required = false) String sessionId,
      @RequestParam(required = false) Boolean clearAll,
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    String key = sessionKey != null ? sessionKey : str(b.get("sessionKey"));
    String sid = sessionId != null ? sessionId : str(b.get("sessionId"));
    Boolean clear =
        clearAll != null
            ? clearAll
            : (b.get("clearAll") instanceof Boolean bool ? bool : null);
    assistant.sessionDelete(key, sid, clear);
    return ResultModel.ok();
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
