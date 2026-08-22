package com.bluedock.org.department.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.org.department.domain.Department;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
  @Mock DepartmentRepository departments;
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  DepartmentService service;

  @BeforeEach
  void setUp() {
    service = new DepartmentService(departments, users, adminGuard, null);
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void add_rejectsShortName() {
    doNothing().when(adminGuard).requireAdmin();
    assertThrows(BusinessException.class, () -> service.add(null, "a", 0L, 2L));
  }

  @Test
  void add_creates() {
    doNothing().when(adminGuard).requireAdmin();
    UserAccount u = new UserAccount();
    u.setUserId(2L);
    when(users.findByUserId(2L)).thenReturn(Optional.of(u));
    when(departments.count()).thenReturn(0);
    when(departments.countOwnedBy(2L)).thenReturn(0);
    when(departments.insert("研发", 0L, 2L)).thenReturn(100L);
    Department d = new Department();
    d.setId(100L);
    d.setName("研发");
    d.setOwnerUserId(2L);
    when(departments.find(100L)).thenReturn(Optional.of(d));

    Map<String, Object> out = service.add(null, "研发", 0L, 2L);
    assertEquals(100L, out.get("id"));
    verify(departments).addMember(100L, 2L);
  }

  @Test
  void sync_mergesMembers() {
    doNothing().when(adminGuard).requireAdmin();
    Department d = new Department();
    d.setId(1L);
    when(departments.find(1L)).thenReturn(Optional.of(d));
    when(departments.collectSubIds(1L)).thenReturn(List.of(2L));
    when(departments.listActiveMemberIds(2L)).thenReturn(List.of(9L, 10L));
    when(departments.listMemberIds(2L)).thenReturn(List.of(9L, 10L, 11L));
    when(departments.isMember(1L, 9L)).thenReturn(false);
    when(departments.isMember(1L, 10L)).thenReturn(true);

    Map<String, Object> out = service.sync(1L);
    assertEquals(1, out.get("syncedCount"));
    assertEquals(1, out.get("alreadyInDeptCount"));
    assertEquals(1, out.get("skippedDisabledCount"));
    verify(departments).addMember(eq(1L), eq(9L));
  }
}
