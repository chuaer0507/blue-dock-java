package com.bluedock.worker.index.handler;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.worker.index.opensearch.OpenSearchIndexSink;
import com.bluedock.worker.index.rebuild.SearchRebuildRunner;
import com.bluedock.worker.index.repo.SearchDocRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexHandler {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexHandler.class);

  private final SearchDocRepository docs;
  private final StringRedisTemplate redis;
  private final OpenSearchIndexSink opensearch;
  private final SearchRebuildRunner rebuild;

  public SearchIndexHandler(
      SearchDocRepository docs,
      StringRedisTemplate redis,
      OpenSearchIndexSink opensearch,
      SearchRebuildRunner rebuild) {
    this.docs = docs;
    this.redis = redis;
    this.opensearch = opensearch;
    this.rebuild = rebuild;
  }

  public void handle(SearchIndexEvent event) {
    if (event == null || event.docType() == null) {
      return;
    }
    if (SearchIndexEvent.ACTION_REBUILD.equals(event.action())) {
      if (event.eventId() != null && !event.eventId().isBlank()) {
        Boolean first =
            redis
                .opsForValue()
                .setIfAbsent(
                    RedisKeys.searchIndexIdempotency(event.eventId()), "1", Duration.ofDays(2));
        if (Boolean.FALSE.equals(first)) {
          log.debug("search rebuild idempotency skip eventId={}", event.eventId());
          return;
        }
      }
      String types =
          event.content() != null && !event.content().isBlank() ? event.content() : event.title();
      rebuild.run(event.eventId(), types);
      return;
    }
    if (event.eventId() != null && !event.eventId().isBlank()) {
      Boolean first =
          redis
              .opsForValue()
              .setIfAbsent(
                  RedisKeys.searchIndexIdempotency(event.eventId()), "1", Duration.ofDays(2));
      if (Boolean.FALSE.equals(first)) {
        log.debug("search index idempotency skip eventId={}", event.eventId());
        return;
      }
    }
    if (SearchIndexEvent.ACTION_DELETE.equals(event.action())) {
      docs.delete(event.docType(), event.refId());
      opensearch.delete(event.docType(), event.refId());
      return;
    }
    docs.upsert(
        event.docType(),
        event.refId(),
        event.userId(),
        event.projectId(),
        event.title(),
        event.content(),
        event.eventId());
    opensearch.upsert(event);
  }
}
