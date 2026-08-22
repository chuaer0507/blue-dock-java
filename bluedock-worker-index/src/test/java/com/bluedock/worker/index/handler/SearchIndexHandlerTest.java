package com.bluedock.worker.index.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.worker.index.opensearch.OpenSearchIndexSink;
import com.bluedock.worker.index.rebuild.SearchRebuildRunner;
import com.bluedock.worker.index.repo.SearchDocRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SearchIndexHandlerTest {
  @Mock SearchDocRepository docs;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock OpenSearchIndexSink opensearch;
  @Mock SearchRebuildRunner rebuild;

  @Test
  void upsert() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    SearchIndexHandler handler = new SearchIndexHandler(docs, redis, opensearch, rebuild);
    handler.handle(
        new SearchIndexEvent(
            "e1", SearchIndexEvent.ACTION_UPSERT, "task", 9L, 1L, 2L, "t", "c"));
    verify(docs).upsert("task", 9L, 1L, 2L, "t", "c", "e1");
    verify(opensearch).upsert(any());
  }

  @Test
  void idempotency_skip() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
    SearchIndexHandler handler = new SearchIndexHandler(docs, redis, opensearch, rebuild);
    handler.handle(
        new SearchIndexEvent(
            "e1", SearchIndexEvent.ACTION_UPSERT, "task", 9L, 1L, 2L, "t", "c"));
    verify(docs, never())
        .upsert(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    verify(opensearch, never()).upsert(any());
  }

  @Test
  void rebuild_dispatches() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    SearchIndexHandler handler = new SearchIndexHandler(docs, redis, opensearch, rebuild);
    handler.handle(
        new SearchIndexEvent(
            "rb1",
            SearchIndexEvent.ACTION_REBUILD,
            "all",
            0L,
            1L,
            0L,
            "project,task",
            "project,task"));
    verify(rebuild).run("rb1", "project,task");
    verify(docs, never())
        .upsert(anyString(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
  }
}
