package com.bluedock.system.apps.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.config.AppsProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AppLifecycleHookClientTest {

  @Test
  void notify_skipsWhenUrlBlank() {
    AppsProperties props = new AppsProperties();
    AppLifecycleHookClient client = new AppLifecycleHookClient(props, new ObjectMapper());
    assertTrue(client.notify("install", "okr", "OKR", "1.0.0"));
  }

  @Test
  void afterMutate_failOpenDoesNotRollbackOrThrow() {
    AppsProperties props = new AppsProperties();
    props.setLifecycleHookFailOpen(true);
    AppLifecycleHookClient client = new AppLifecycleHookClient(props, new ObjectMapper());
    AtomicBoolean rolled = new AtomicBoolean(false);
    client.afterMutate(false, "install", "okr", () -> rolled.set(true));
    assertEquals(false, rolled.get());
  }

  @Test
  void afterMutate_strictRollsBackAndThrows() {
    AppsProperties props = new AppsProperties();
    props.setLifecycleHookFailOpen(false);
    AppLifecycleHookClient client = new AppLifecycleHookClient(props, new ObjectMapper());
    AtomicBoolean rolled = new AtomicBoolean(false);
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> client.afterMutate(false, "install", "okr", () -> rolled.set(true)));
    assertTrue(rolled.get());
    assertEquals(I18nKeys.APPS_LIFECYCLE_HOOK_FAILED, ex.getMessageKey());
  }
}
