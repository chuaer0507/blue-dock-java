package com.bluedock.boot.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxDispatchServiceTest {

  @Mock JdbcTemplate jdbc;
  @Mock KafkaTemplate<String, String> kafka;

  @Test
  void pollOnce_empty() {
    OutboxProperties props = new OutboxProperties();
    OutboxDispatchService svc = new OutboxDispatchService(jdbc, kafka, props);
    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<?>>any(), eq(50)))
        .thenReturn(List.of());
    assertEquals(0, svc.pollOnce());
  }

  @Test
  void pollOnce_sendsKafkaAndMarksPublished() throws Exception {
    OutboxProperties props = new OutboxProperties();
    props.setBatchSize(10);
    OutboxDispatchService svc = new OutboxDispatchService(jdbc, kafka, props);

    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("id")).thenReturn(42L);
    when(rs.getString("topic")).thenReturn("bluedock.notify.send");
    when(rs.getString("message_key")).thenReturn("evt-1");
    when(rs.getString("payload")).thenReturn("{\"a\":1}");

    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<?>>any(), eq(10)))
        .thenAnswer(
            inv -> {
              RowMapper<?> mapper = inv.getArgument(1);
              return List.of(mapper.mapRow(rs, 0));
            });
    when(kafka.send(eq("bluedock.notify.send"), eq("evt-1"), eq("{\"a\":1}")))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertEquals(1, svc.pollOnce());
    verify(jdbc).update(anyString(), any(), eq(42L));
  }
}
