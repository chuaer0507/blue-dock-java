package com.bluedock.messenger.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.bot.UserBotWebhookPublisher;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.repo.DialogRepository;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class UserBotWebhookDispatchServiceTest {
  @Mock DialogRepository dialogs;
  @Mock UserAccountRepository users;
  @Mock JdbcTemplate jdbc;
  @Mock TokenService tokens;
  @Mock ObjectProvider<UserBotWebhookPublisher> publisherProvider;
  @Mock UserBotWebhookPublisher publisher;
  @Mock StringRedisTemplate redis;
  UserBotWebhookDispatchService service;

  @BeforeEach
  void setUp() {
    service =
        new UserBotWebhookDispatchService(
            dialogs, users, jdbc, tokens, publisherProvider, new ObjectMapper(), redis);
  }

  @Test
  void parseMentions() {
    assertEquals(
        List.of(9L, 11L),
        UserBotWebhookDispatchService.parseMentions(
            "<span class=\"mention user\" data-id=\"9\">@a</span> [:@:11:b:]"));
    assertEquals(
        List.of(0L),
        UserBotWebhookDispatchService.parseMentions("<span class=\"mention all\">@所有人</span>"));
  }

  @Test
  void skipSlashCommand() {
    Dialog d = new Dialog();
    d.setId(1L);
    d.setType("user");
    DialogMessage m = new DialogMessage();
    m.setId(2L);
    m.setUserId(3L);
    UserAccount sender = new UserAccount();
    sender.setIsBot(0);
    when(users.findByUserId(3L)).thenReturn(Optional.of(sender));
    service.afterTextMessage(d, m, "/webhook x");
    verify(publisherProvider, never()).getIfAvailable();
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishWhenBotMember() throws Exception {
    Dialog d = new Dialog();
    d.setId(1L);
    d.setType("user");
    DialogMessage m = new DialogMessage();
    m.setId(2L);
    m.setUserId(3L);
    m.setReplyId(0L);
    UserAccount sender = new UserAccount();
    sender.setUserId(3L);
    sender.setIsBot(0);
    sender.setEmail("u@a.com");
    sender.setNickname("U");
    UserAccount bot = new UserAccount();
    bot.setUserId(9L);
    bot.setIsBot(1);
    when(users.findByUserId(3L)).thenReturn(Optional.of(sender));
    when(users.findByUserId(9L)).thenReturn(Optional.of(bot));
    when(dialogs.listMemberUserIds(1L)).thenReturn(List.of(3L, 9L));
    when(jdbc.query(any(String.class), any(RowMapper.class), eq(9L)))
        .thenAnswer(
            inv -> {
              RowMapper<Object> mapper = inv.getArgument(1);
              ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
              when(rs.getString("webhook_url")).thenReturn("https://example.com/hook");
              when(rs.getString("webhook_events")).thenReturn("[\"message\"]");
              return List.of(mapper.mapRow(rs, 0));
            });
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    when(tokens.issue(9L)).thenReturn("bot-tok");
    when(tokens.issue(3L)).thenReturn("user-tok");

    service.afterTextMessage(d, m, "hello");
    ArgumentCaptor<UserBotWebhookEvent> cap = ArgumentCaptor.forClass(UserBotWebhookEvent.class);
    verify(publisher).publish(cap.capture());
    assertEquals("https://example.com/hook", cap.getValue().webhookUrl());
    assertEquals(9L, cap.getValue().botUserId());
  }
}
