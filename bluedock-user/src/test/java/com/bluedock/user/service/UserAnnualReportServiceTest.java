package com.bluedock.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAnnualReportServiceTest {
  @Mock UserAccountRepository users;
  @Mock JdbcTemplate jdbc;

  UserAnnualReportService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    service = new UserAnnualReportService(users, jdbc);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void report_selfCurrentYear() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("a@b.c");
    u.setNickname("N");
    u.setUserImage("");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(jdbc.queryForList(anyString(), eq(1L)))
        .thenReturn(
            List.of(
                Map.of(
                    "createdAt",
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    "onlineAt",
                    LocalDateTime.of(Year.now().getValue(), 6, 1, 12, 0))));
    lenient()
        .when(jdbc.queryForList(anyString(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(jdbc.queryForList(anyString(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    lenient()
        .when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
        .thenReturn(0L);

    Map<String, Object> report = service.report(null);
    assertEquals(Year.now().getValue(), report.get("year"));
    assertEquals("2024-01-01", report.get("hireDate"));
    @SuppressWarnings("unchecked")
    Map<String, Object> tasks = (Map<String, Object>) report.get("tasks");
    assertEquals(0L, tasks.get("total"));
  }

  @Test
  void report_invalidYear() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    assertThrows(BusinessException.class, () -> service.report(1999));
  }
}
