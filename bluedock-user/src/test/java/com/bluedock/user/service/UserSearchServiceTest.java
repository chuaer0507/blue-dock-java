package com.bluedock.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.user.web.dto.UserSearchView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSearchServiceTest {
  @Mock UserAccountRepository users;

  UserSearchService service;

  @BeforeEach
  void setUp() {
    service = new UserSearchService(users);
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  void search_takeMode_defaultsExcludeDisabledAndBot() {
    UserAccount u = sample(9L, "a@b.com", "Alice");
    when(users.search(eq("al"), eq(0), eq(0), isNull(), isNull(), eq(""), eq(10), eq(0)))
        .thenReturn(List.of(u));

    Map<String, Object> out = service.search("al", null, null, null, null, null, null, null, null);
    @SuppressWarnings("unchecked")
    List<UserSearchView> list = (List<UserSearchView>) out.get("list");
    assertEquals(1, list.size());
    assertEquals(9L, list.get(0).userId());
    assertFalse(out.containsKey("total"));
  }

  @Test
  void search_pageMode_returnsTotal() {
    when(users.countSearch(eq(""), eq(1), eq(1), eq(3L), isNull())).thenReturn(2);
    when(users.search(eq(""), eq(1), eq(1), eq(3L), isNull(), eq("asc"), eq(20), eq(0)))
        .thenReturn(List.of(sample(1L, "a@x.com", "A"), sample(2L, "b@x.com", "B")));

    Map<String, Object> out = service.search("", 1, 1, 3L, null, "asc", null, 1, 20);
    assertEquals(2, out.get("total"));
    assertEquals(1, out.get("page"));
    assertEquals(20, out.get("pageSize"));
    assertEquals(2, ((List<?>) out.get("list")).size());
  }

  @Test
  void searchAi_ok() {
    when(users.listAiBots(50)).thenReturn(List.of(sample(8L, "ai-openai@bot.system", "ChatGPT")));
    Map<String, Object> out = service.searchAi(null);
    @SuppressWarnings("unchecked")
    List<UserSearchView> list = (List<UserSearchView>) out.get("list");
    assertEquals(1, list.size());
    assertEquals("ai-openai@bot.system", list.get(0).email());
    verify(users).listAiBots(50);
  }

  private static UserAccount sample(long id, String email, String nick) {
    UserAccount u = new UserAccount();
    u.setUserId(id);
    u.setEmail(email);
    u.setNickname(nick);
    u.setProfession("dev");
    u.setUserImage("");
    u.setNameAz("A");
    u.setIsBot(0);
    return u;
  }
}
