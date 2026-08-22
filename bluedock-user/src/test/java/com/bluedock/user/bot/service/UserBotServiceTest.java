package com.bluedock.user.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.user.bot.repo.UserBotRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserBotServiceTest {
  @Mock UserBotRepository bots;
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  UserBotService service;

  @BeforeEach
  void setUp() {
    service =
        new UserBotService(
            bots, users, adminGuard, new BCryptPasswordEncoder(), new ObjectMapper());
    AuthContext.set(new AuthUser(3L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void edit_create() {
    when(bots.countOwned(3L)).thenReturn(0);
    when(bots.findOwned(eq(3L), anyLong()))
        .thenReturn(
            Optional.of(
                Map.of(
                    "clearDay",
                    90,
                    "webhookUrl",
                    "",
                    "webhookEvents",
                    "[\"message\"]")));
    when(users.findByUserId(anyLong()))
        .thenAnswer(
            inv -> {
              UserAccount u = new UserAccount();
              u.setUserId(inv.getArgument(0));
              u.setIsBot(1);
              u.setNickname("助手");
              u.setEmail("x@bot.user");
              u.setUserImage("");
              return Optional.of(u);
            });

    Map<String, Object> out = service.edit(null, "助手", null, null, null, null);
    assertEquals("助手", out.get("name"));
    verify(users).insert(any(UserAccount.class));
    verify(bots).insert(eq(3L), anyLong(), eq(90), eq(""), anyString());
  }

  @Test
  void delete_systemForbidden() {
    UserAccount u = new UserAccount();
    u.setUserId(9L);
    u.setIsBot(1);
    u.setEmail("system-msg@bot.system");
    when(users.findByUserId(9L)).thenReturn(Optional.of(u));
    assertThrows(BusinessException.class, () -> service.delete(9L, "cleanup"));
  }
}
