package com.bluedock.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.search.engine.SearchEngine;
import com.bluedock.search.web.dto.SearchHitView;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {
  @Mock SearchEngine search;
  @InjectMocks SearchService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void key_required() {
    assertThrows(BusinessException.class, () -> service.project("  ", 10));
  }

  @Test
  void project_ok() {
    when(search.projects(1L, "demo", 20))
        .thenReturn(List.of(new SearchHitView("project", 9L, "demo", "", 9L)));
    assertEquals(1, service.project("demo", null).size());
    verify(search).projects(1L, "demo", 20);
  }
}
