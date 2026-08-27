package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.system.config.SystemProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {
  @Mock AdminGuard adminGuard;
  @Mock JdbcTemplate jdbc;
  @TempDir Path dir;

  SystemProperties props;
  LicenseService service;

  @BeforeEach
  void setUp() {
    props = new SystemProperties();
    props.setLicensePath(dir.resolve("license.json").toString());
    props.setMachineSn("SN-TEST");
    service = new LicenseService(props, new ObjectMapper(), adminGuard, jdbc);
  }

  @Test
  void save_structuredJson_andStatus() throws Exception {
    when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class)))
        .thenReturn(2);
    String body =
        """
        {"license":"opaque","people":10,"sn":"SN-TEST","macAddresses":[],"expiredAt":"2099-12-31"}
        """;
    Map<String, Object> out = service.save(body);
    assertTrue((Boolean) out.get("ok"));
    assertEquals(10, ((Map<?, ?>) out.get("info")).get("people"));
    assertTrue(Files.exists(Path.of(props.getLicensePath())));
  }

  @Test
  void status_reportsExpired() throws Exception {
    when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class)))
        .thenReturn(1);
    Files.writeString(
        Path.of(props.getLicensePath()),
        """
        {"license":"x","people":2,"sn":"","macAddresses":[],"expiredAt":"2020-01-01"}
        """);
    Map<String, Object> out = service.status();
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) out.get("error");
    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(s -> s.contains("过期") || s.toLowerCase().contains("expired")));
  }

  @Test
  void assertCanAddUser_blocksWhenFull() throws Exception {
    when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class)))
        .thenReturn(5);
    Files.writeString(
        Path.of(props.getLicensePath()),
        """
        {"license":"x","people":5,"sn":"","macAddresses":[],"expiredAt":""}
        """);
    assertThrows(BusinessException.class, () -> service.assertCanAddUser());
  }

  @Test
  void requiresBinding_rules() {
    assertTrue(LicenseService.requiresBinding(0));
    assertTrue(LicenseService.requiresBinding(10));
    assertFalse(LicenseService.requiresBinding(3));
    assertFalse(LicenseService.requiresBinding(1));
  }

  @Test
  void isExpired_foreverSafe() {
    assertFalse(LicenseService.isExpired(""));
    assertFalse(LicenseService.isExpired("forever"));
    assertTrue(LicenseService.isExpired("2020-01-01"));
  }
}
