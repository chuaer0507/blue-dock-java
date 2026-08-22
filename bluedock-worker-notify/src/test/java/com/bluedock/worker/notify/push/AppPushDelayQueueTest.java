package com.bluedock.worker.notify.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppPushDelayQueueTest {
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock ZSetOperations<String, String> zset;

  AppPushDelayQueue queue;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForZSet()).thenReturn(zset);
    queue = new AppPushDelayQueue(redis, new ObjectMapper());
  }

  @Test
  void enqueue_writesZsetAndJob() {
    queue.enqueue("e1", List.of(1L, 2L), "t", "b", Map.of("messageId", 9));
    ArgumentCaptor<String> jobKey = ArgumentCaptor.forClass(String.class);
    verify(values).set(jobKey.capture(), anyString(), eq(Duration.ofHours(1)));
    assertTrue(jobKey.getValue().contains(":appPush:delay:job:"));
    verify(zset).add(eq(RedisKeys.appPushDelayQueue()), anyString(), anyDouble());
  }

  @Test
  void pollDue_loadsJobs() throws Exception {
    String jobId = "job1";
    String json =
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "eventId",
                    "e1",
                    "userIds",
                    List.of(3),
                    "title",
                    "Hi",
                    "body",
                    "x",
                    "data",
                    Map.of("messageId", 8)));
    Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
    tuples.add(ZSetOperations.TypedTuple.of(jobId, 1.0));
    when(zset.rangeByScoreWithScores(eq(RedisKeys.appPushDelayQueue()), eq(0.0), anyDouble(), eq(0L), eq(50L)))
        .thenReturn(tuples);
    when(zset.remove(RedisKeys.appPushDelayQueue(), jobId)).thenReturn(1L);
    when(values.get(RedisKeys.appPushDelayJob(jobId))).thenReturn(json);

    List<AppPushDelayQueue.DelayedJob> jobs = queue.pollDue(50);
    assertEquals(1, jobs.size());
    assertEquals("e1", jobs.get(0).eventId());
    assertEquals(List.of(3L), jobs.get(0).userIds());
    assertEquals("Hi", jobs.get(0).title());
  }
}
