package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.system.repo.SettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LdapSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;

  LdapSettingService service;

  @BeforeEach
  void setUp() {
    service = new LdapSettingService(settings, new ObjectMapper(), adminGuard, writeGuard);
  }

  @Test
  void get_returnsDefaultsWhenUnset() {
    when(settings.findSettingJson(LdapSettingService.SETTING_NAME)).thenReturn(Optional.empty());

    Map<String, Object> view = service.get();

    assertEquals("close", view.get("ldapOpen"));
    assertEquals("389", view.get("ldapPort"));
    assertEquals("cn", view.get("ldapLoginAttr"));
    verify(adminGuard).requireAdmin();
  }

  @Test
  void save_mergesInputWithDefaults() {
    when(settings.findSettingJson(LdapSettingService.SETTING_NAME)).thenReturn(Optional.empty());

    Map<String, Object> saved = service.save(Map.of("ldapOpen", "open", "ldapHost", "ldap.example.com"));

    assertEquals("open", saved.get("ldapOpen"));
    assertEquals("ldap.example.com", saved.get("ldapHost"));
    assertEquals("389", saved.get("ldapPort"));
    verify(writeGuard).requireWritable();
    verify(settings)
        .upsert(eq(LdapSettingService.SETTING_NAME), contains("ldap.example.com"));
  }
}
