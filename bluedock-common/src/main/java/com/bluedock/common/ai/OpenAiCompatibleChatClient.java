package com.bluedock.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容客户端：chat completions（同步/流式）、embeddings、audio transcriptions。
 *
 * <p>可用于官方 OpenAI、DeepSeek、自建网关等；不含业务提示词与密钥解析。
 */
public class OpenAiCompatibleChatClient {
  private final HttpClient http;
  private final ObjectMapper json;
  private final Duration timeout;

  public OpenAiCompatibleChatClient(ObjectMapper json) {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), json, Duration.ofSeconds(60));
  }

  public OpenAiCompatibleChatClient(HttpClient http, ObjectMapper json, Duration timeout) {
    this.http = http;
    this.json = json == null ? new ObjectMapper() : json;
    this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
  }

  /**
   * OpenAI 兼容流式 chat；按 delta 回调文本片段；上游结束或 {@code [DONE]} 后返回。
   *
   * @param onDelta 非空文本片段；不得为 null
   */
  public void chatCompletionsStream(
      String baseUrl,
      String apiKey,
      String model,
      List<Map<String, String>> messages,
      java.util.function.Consumer<String> onDelta) {
    if (onDelta == null) {
      throw new OpenAiChatException("missing onDelta");
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new OpenAiChatException("missing apiKey");
    }
    if (model == null || model.isBlank()) {
      throw new OpenAiChatException("missing model");
    }
    if (messages == null || messages.isEmpty()) {
      throw new OpenAiChatException("missing messages");
    }
    String url = completionsUrl(baseUrl);
    try {
      ObjectNode body = json.createObjectNode();
      body.put("model", model.trim());
      body.put("stream", true);
      ArrayNode arr = body.putArray("messages");
      for (Map<String, String> m : messages) {
        if (m == null) {
          continue;
        }
        String role = m.getOrDefault("role", "user");
        String content = m.getOrDefault("content", "");
        ObjectNode row = arr.addObject();
        row.put("role", role == null ? "user" : role);
        row.put("content", content == null ? "" : content);
      }
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofMinutes(3))
              .header("Authorization", "Bearer " + apiKey.trim())
              .header("Content-Type", "application/json")
              .header("Accept", "text/event-stream")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      HttpResponse<java.io.InputStream> resp =
          http.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        String errBody;
        try (java.io.InputStream in = resp.body()) {
          errBody = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new OpenAiChatException("http " + resp.statusCode() + ": " + truncate(errBody));
      }
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty()) {
            continue;
          }
          if (!line.startsWith("data:")) {
            continue;
          }
          String payload = line.substring(5).trim();
          if (payload.isEmpty()) {
            continue;
          }
          if ("[DONE]".equals(payload)) {
            break;
          }
          String delta = extractDeltaContent(json.readTree(payload));
          if (delta != null && !delta.isEmpty()) {
            onDelta.accept(delta);
          }
        }
      }
    } catch (OpenAiChatException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new OpenAiChatException(e.toString(), e);
    }
  }

  /** 流式 choice.delta.content；兼容部分网关顶层 text。 */
  static String extractDeltaContent(JsonNode root) {
    if (root == null) {
      return null;
    }
    JsonNode choices = root.path("choices");
    if (choices.isArray() && !choices.isEmpty()) {
      JsonNode delta = choices.get(0).path("delta");
      if (delta.hasNonNull("content")) {
        return delta.get("content").asText();
      }
      if (choices.get(0).hasNonNull("text")) {
        return choices.get(0).get("text").asText();
      }
    }
    return null;
  }

  /**
   * @param baseUrl 如 {@code https://api.openai.com} 或已含 {@code /v1}
   * @param apiKey Bearer token
   * @param model 模型 id
   * @param messages {@code [{role,content},...]}
   * @return assistant 文本；失败抛 {@link OpenAiChatException}
   */
  public String chatCompletions(
      String baseUrl, String apiKey, String model, List<Map<String, String>> messages) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new OpenAiChatException("missing apiKey");
    }
    if (model == null || model.isBlank()) {
      throw new OpenAiChatException("missing model");
    }
    if (messages == null || messages.isEmpty()) {
      throw new OpenAiChatException("missing messages");
    }
    String url = completionsUrl(baseUrl);
    try {
      ObjectNode body = json.createObjectNode();
      body.put("model", model.trim());
      ArrayNode arr = body.putArray("messages");
      for (Map<String, String> m : messages) {
        if (m == null) {
          continue;
        }
        String role = m.getOrDefault("role", "user");
        String content = m.getOrDefault("content", "");
        ObjectNode row = arr.addObject();
        row.put("role", role == null ? "user" : role);
        row.put("content", content == null ? "" : content);
      }
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(timeout)
              .header("Authorization", "Bearer " + apiKey.trim())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new OpenAiChatException("http " + resp.statusCode() + ": " + truncate(resp.body()));
      }
      String content = extractContent(json.readTree(resp.body()));
      if (content == null || content.isBlank()) {
        throw new OpenAiChatException("empty content");
      }
      return content.trim();
    } catch (OpenAiChatException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new OpenAiChatException(e.toString(), e);
    }
  }

  static String completionsUrl(String baseUrl) {
    String base = baseUrl == null ? "" : baseUrl.trim();
    if (base.isEmpty()) {
      base = "https://api.openai.com";
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.endsWith("/chat/completions")) {
      return base;
    }
    if (base.endsWith("/v1")) {
      return base + "/chat/completions";
    }
    return base + "/v1/chat/completions";
  }

  /**
   * OpenAI 兼容 {@code /v1/audio/transcriptions}（multipart）。
   *
   * @param prompt 可选上下文提示
   * @return 识别文本
   */
  public String audioTranscriptions(
      String baseUrl,
      String apiKey,
      String model,
      byte[] fileBytes,
      String filename,
      String contentType,
      String prompt) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new OpenAiChatException("missing apiKey");
    }
    if (model == null || model.isBlank()) {
      throw new OpenAiChatException("missing model");
    }
    if (fileBytes == null || fileBytes.length == 0) {
      throw new OpenAiChatException("missing audio");
    }
    String name = filename == null || filename.isBlank() ? "audio.mp3" : filename.trim();
    String mime =
        contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
    String boundary = "----TaskFormBoundary" + java.util.UUID.randomUUID().toString().replace("-", "");
    byte[] body = buildTranscriptionMultipart(boundary, model.trim(), fileBytes, name, mime, prompt);
    String url = transcriptionsUrl(baseUrl);
    try {
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(timeout)
              .header("Authorization", "Bearer " + apiKey.trim())
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new OpenAiChatException("http " + resp.statusCode() + ": " + truncate(resp.body()));
      }
      JsonNode root = json.readTree(resp.body());
      String text = root != null && root.hasNonNull("text") ? root.get("text").asText("") : "";
      if (text.isBlank()) {
        throw new OpenAiChatException("empty transcription");
      }
      return text.trim();
    } catch (OpenAiChatException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new OpenAiChatException(e.toString(), e);
    }
  }

  static String transcriptionsUrl(String baseUrl) {
    String base = baseUrl == null ? "" : baseUrl.trim();
    if (base.isEmpty()) {
      base = "https://api.openai.com";
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.endsWith("/audio/transcriptions")) {
      return base;
    }
    if (base.endsWith("/v1")) {
      return base + "/audio/transcriptions";
    }
    if (base.endsWith("/chat/completions")) {
      return base.substring(0, base.length() - "/chat/completions".length()) + "/audio/transcriptions";
    }
    return base + "/v1/audio/transcriptions";
  }

  /**
   * OpenAI 兼容 {@code /v1/embeddings}；按输入顺序返回向量。
   *
   * @param model 如 {@code text-embedding-3-small}
   * @param inputs 非空文本列表（≤100）
   */
  public List<float[]> embeddings(String baseUrl, String apiKey, String model, List<String> inputs) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new OpenAiChatException("missing apiKey");
    }
    if (model == null || model.isBlank()) {
      throw new OpenAiChatException("missing model");
    }
    if (inputs == null || inputs.isEmpty()) {
      throw new OpenAiChatException("missing inputs");
    }
    if (inputs.size() > 100) {
      throw new OpenAiChatException("too many inputs");
    }
    String url = embeddingsUrl(baseUrl);
    try {
      ObjectNode body = json.createObjectNode();
      body.put("model", model.trim());
      ArrayNode arr = body.putArray("input");
      for (String t : inputs) {
        arr.add(t == null ? "" : t);
      }
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(timeout)
              .header("Authorization", "Bearer " + apiKey.trim())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new OpenAiChatException("http " + resp.statusCode() + ": " + truncate(resp.body()));
      }
      return extractEmbeddings(json.readTree(resp.body()), inputs.size());
    } catch (OpenAiChatException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new OpenAiChatException(e.toString(), e);
    }
  }

  static String embeddingsUrl(String baseUrl) {
    String base = baseUrl == null ? "" : baseUrl.trim();
    if (base.isEmpty()) {
      base = "https://api.openai.com";
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.endsWith("/embeddings")) {
      return base;
    }
    if (base.endsWith("/v1")) {
      return base + "/embeddings";
    }
    if (base.endsWith("/chat/completions")) {
      return base.substring(0, base.length() - "/chat/completions".length()) + "/embeddings";
    }
    if (base.endsWith("/audio/transcriptions")) {
      return base.substring(0, base.length() - "/audio/transcriptions".length()) + "/embeddings";
    }
    return base + "/v1/embeddings";
  }

  static List<float[]> extractEmbeddings(JsonNode root, int expected) {
    if (root == null) {
      throw new OpenAiChatException("empty embeddings");
    }
    JsonNode data = root.path("data");
    if (!data.isArray() || data.isEmpty()) {
      throw new OpenAiChatException("empty embeddings");
    }
    float[][] slots = new float[expected][];
    for (JsonNode row : data) {
      int idx = row.path("index").asInt(-1);
      JsonNode emb = row.path("embedding");
      if (!emb.isArray() || emb.isEmpty()) {
        continue;
      }
      float[] vec = new float[emb.size()];
      for (int i = 0; i < emb.size(); i++) {
        vec[i] = (float) emb.get(i).asDouble();
      }
      if (idx >= 0 && idx < expected) {
        slots[idx] = vec;
      }
    }
    List<float[]> out = new ArrayList<>(expected);
    for (int i = 0; i < expected; i++) {
      if (slots[i] == null) {
        throw new OpenAiChatException("missing embedding index " + i);
      }
      out.add(slots[i]);
    }
    return out;
  }

  /** 余弦相似度；零向量返回 0。 */
  public static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null || a.length == 0 || a.length != b.length) {
      return 0;
    }
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    if (na == 0 || nb == 0) {
      return 0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  static byte[] buildTranscriptionMultipart(
      String boundary,
      String model,
      byte[] fileBytes,
      String filename,
      String contentType,
      String prompt) {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    try {
      writeFormField(out, boundary, "model", model);
      if (prompt != null && !prompt.isBlank()) {
        writeFormField(out, boundary, "prompt", prompt.trim());
      }
      out.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(
          ("Content-Disposition: form-data; name=\"file\"; filename=\""
                  + filename.replace("\"", "")
                  + "\"\r\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(
          ("Content-Type: " + contentType + "\r\n\r\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(fileBytes);
      out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new OpenAiChatException(e.toString(), e);
    }
    return out.toByteArray();
  }

  private static void writeFormField(
      java.io.ByteArrayOutputStream out, String boundary, String name, String value)
      throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static String extractContent(JsonNode root) {
    if (root == null) {
      return null;
    }
    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.isEmpty()) {
      return null;
    }
    JsonNode msg = choices.get(0).path("message");
    if (msg.hasNonNull("content")) {
      return msg.get("content").asText();
    }
    // 部分网关把文本放在 choice 顶层
    if (choices.get(0).hasNonNull("text")) {
      return choices.get(0).get("text").asText();
    }
    return null;
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 300 ? s : s.substring(0, 300);
  }
}
