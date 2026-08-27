package com.bluedock.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SearchRebuildServiceTest {
  @Mock SearchIndexPublisher publisher;
  @Mock UserAccountRepository users;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;

  SearchRebuildService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    lenient().when(redis.opsForValue()).thenReturn(values);
    service = new SearchRebuildService(publisher, users, redis, new ObjectMapper());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void start_requiresAdmin() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setIdentity("member");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    assertThrows(BusinessException.class, () -> service.start("all"));
  }

  @Test
  void start_publishesRebuild() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setIdentity("admin");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(values.setIfAbsent(eq(RedisKeys.searchRebuildLock()), eq("1"), any(Duration.class)))
        .thenReturn(true);

    Map<String, Object> status = service.start("project,task");
    assertEquals("queued", status.get("state"));
    ArgumentCaptor<SearchIndexEvent> cap = ArgumentCaptor.forClass(SearchIndexEvent.class);
    verify(publisher).publish(cap.capture());
    assertEquals(SearchIndexEvent.ACTION_REBUILD, cap.getValue().action());
    assertEquals("project,task", cap.getValue().content());
    verify(values).set(eq(RedisKeys.searchRebuildStatus()), anyString(), any(Duration.class));
  }
}
