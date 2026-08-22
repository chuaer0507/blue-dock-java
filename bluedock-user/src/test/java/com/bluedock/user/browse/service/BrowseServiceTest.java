package com.bluedock.user.browse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.user.browse.repo.RecentItemRepository;
import com.bluedock.user.browse.repo.TaskBrowseRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BrowseServiceTest {
  @Mock TaskBrowseRepository taskBrowses;
  @Mock RecentItemRepository recentItems;
  @Mock BrowseRecorder recorder;
  @Mock JdbcTemplate jdbc;
  BrowseService service;

  @BeforeEach
  void setUp() {
    service = new BrowseService(taskBrowses, recentItems, recorder, jdbc, new ObjectMapper());
    AuthContext.set(new AuthUser(7L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void taskBrowseSave_records() {
    when(jdbc.queryForObject(
            eq("SELECT COUNT(1) FROM bluedock_tasks WHERE id = ? AND deleted_at IS NULL"),
            eq(Integer.class),
            eq(55L)))
        .thenReturn(1);
    Map<String, Object> out = service.taskBrowseSave(55L);
    assertEquals(0, out.size());
    verify(recorder).recordTask(7L, 55L);
  }

  @Test
  void recentDelete_missing() {
    when(recentItems.findOwned(7L, 9L)).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> service.recentDelete(9L));
  }

  @Test
  void taskBrowseClean_keep() {
    when(taskBrowses.clean(7L, 10)).thenReturn(3);
    assertEquals(3, service.taskBrowseClean(10).get("deletedCount"));
  }
}
