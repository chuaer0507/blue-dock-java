package com.bluedock.common.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OpenAiCompatibleChatClientTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void completionsUrl_normalizes() {
    assertEquals(
        "https://api.openai.com/v1/chat/completions",
        OpenAiCompatibleChatClient.completionsUrl("https://api.openai.com"));
    assertEquals(
        "https://api.deepseek.com/v1/chat/completions",
        OpenAiCompatibleChatClient.completionsUrl("https://api.deepseek.com/v1/"));
    assertEquals(
        "https://gw.example/v1/chat/completions",
        OpenAiCompatibleChatClient.completionsUrl("https://gw.example/v1/chat/completions"));
  }

  @Test
  void extractContent_fromChoices() throws Exception {
    var root =
        json.readTree(
            """
            {"choices":[{"message":{"role":"assistant","content":"你好"}}]}
            """);
    assertEquals("你好", OpenAiCompatibleChatClient.extractContent(root));
    assertNull(OpenAiCompatibleChatClient.extractContent(json.readTree("{}")));
  }

  @Test
  void extractDeltaContent_fromChoices() throws Exception {
    var root =
        json.readTree(
            """
            {"choices":[{"delta":{"content":"你好"}}]}
            """);
    assertEquals("你好", OpenAiCompatibleChatClient.extractDeltaContent(root));
    assertNull(OpenAiCompatibleChatClient.extractDeltaContent(json.readTree("{}")));
  }

  @Test
  void embeddingsUrl_normalizes() {
    assertEquals(
        "https://api.openai.com/v1/embeddings",
        OpenAiCompatibleChatClient.embeddingsUrl("https://api.openai.com"));
    assertEquals(
        "https://api.openai.com/v1/embeddings",
        OpenAiCompatibleChatClient.embeddingsUrl("https://api.openai.com/v1/chat/completions"));
  }

  @Test
  void extractEmbeddings_andCosine() throws Exception {
    var root =
        json.readTree(
            """
            {"data":[{"index":1,"embedding":[0,1]},{"index":0,"embedding":[1,0]}]}
            """);
    var vecs = OpenAiCompatibleChatClient.extractEmbeddings(root, 2);
    assertEquals(2, vecs.size());
    assertEquals(1f, vecs.get(0)[0]);
    assertEquals(1f, vecs.get(1)[1]);
    assertEquals(0.0, OpenAiCompatibleChatClient.cosineSimilarity(vecs.get(0), vecs.get(1)), 1e-6);
    assertEquals(1.0, OpenAiCompatibleChatClient.cosineSimilarity(vecs.get(0), vecs.get(0)), 1e-6);
  }

  @Test
  void audioTranscriptions_postsMultipartAndParses() throws Exception {
    HttpClient http = Mockito.mock(HttpClient.class);
    HttpResponse<String> resp = mockStringResponse();
    Mockito.when(resp.statusCode()).thenReturn(200);
    Mockito.when(resp.body()).thenReturn("{\"text\":\"你好世界\"}");
    ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
    Mockito.when(http.send(cap.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(resp);

    OpenAiCompatibleChatClient client =
        new OpenAiCompatibleChatClient(http, json, java.time.Duration.ofSeconds(5));
    String out =
        client.audioTranscriptions(
            "https://api.openai.com",
            "sk-test",
            "gpt-4o-mini-transcribe",
            new byte[] {1, 2, 3},
            "a.mp3",
            "audio/mp3",
            "prefer Chinese");
    assertEquals("你好世界", out);
    HttpRequest req = cap.getValue();
    assertTrue(req.uri().toString().endsWith("/v1/audio/transcriptions"));
    assertEquals("Bearer sk-test", req.headers().firstValue("Authorization").orElse(""));
    assertTrue(req.headers().firstValue("Content-Type").orElse("").startsWith("multipart/form-data"));
  }

  @Test
  void chatCompletions_postsBearerAndParses() throws Exception {
    HttpClient http = Mockito.mock(HttpClient.class);
    HttpResponse<String> resp = mockStringResponse();
    Mockito.when(resp.statusCode()).thenReturn(200);
    Mockito.when(resp.body())
        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"整理后正文\"}}]}");
    ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
    Mockito.when(http.send(cap.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(resp);

    OpenAiCompatibleChatClient client =
        new OpenAiCompatibleChatClient(http, json, java.time.Duration.ofSeconds(5));
    String out =
        client.chatCompletions(
            "https://api.openai.com",
            "sk-test",
            "gpt-4o-mini",
            List.of(Map.of("role", "user", "content", "hi")));
    assertEquals("整理后正文", out);
    HttpRequest req = cap.getValue();
    assertTrue(req.uri().toString().endsWith("/v1/chat/completions"));
    assertEquals("Bearer sk-test", req.headers().firstValue("Authorization").orElse(""));
  }

  @Test
  void chatCompletions_httpError() throws Exception {
    HttpClient http = Mockito.mock(HttpClient.class);
    HttpResponse<String> resp = mockStringResponse();
    Mockito.when(resp.statusCode()).thenReturn(401);
    Mockito.when(resp.body()).thenReturn("{\"error\":\"no\"}");
    Mockito.when(http.send(Mockito.any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(resp);
    OpenAiCompatibleChatClient client =
        new OpenAiCompatibleChatClient(http, json, java.time.Duration.ofSeconds(5));
    assertThrows(
        OpenAiChatException.class,
        () ->
            client.chatCompletions(
                "https://api.openai.com", "k", "m", List.of(Map.of("role", "user", "content", "x"))));
  }

  /** Class 字面量擦除泛型，集中一次 unchecked 转换。 */
  @SuppressWarnings("unchecked")
  private static HttpResponse<String> mockStringResponse() {
    return (HttpResponse<String>) Mockito.mock(HttpResponse.class);
  }
}
