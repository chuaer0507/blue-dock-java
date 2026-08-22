package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.system.config.BlueDockPublicProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemDemoAndUpdateLogServiceTest {
  @Test
  void demo_requiresConfig() {
    BlueDockPublicProperties props = new BlueDockPublicProperties();
    SystemDemoService service = new SystemDemoService(props);
    assertThrows(BusinessException.class, service::demo);
  }

  @Test
  void demo_ok() {
    BlueDockPublicProperties props = new BlueDockPublicProperties();
    props.getDemo().setAccount("demo@bluedock.local");
    props.getDemo().setPassword("Demo123!");
    Map<String, Object> out = new SystemDemoService(props).demo();
    assertEquals("demo@bluedock.local", out.get("account"));
    assertEquals("Demo123!", out.get("password"));
  }

  @Test
  void parseSections_ok() {
    String md =
        """
        # Changelog
        ## [1.1.0] - 2026-01-02
        - a
        ## [1.0.0] - 2026-01-01
        - b
        """;
    List<Map<String, String>> list = SystemUpdateLogService.parseSections(md, 10);
    assertEquals(2, list.size());
    assertEquals("1.1.0", list.get(0).get("title"));
    assertTrue(list.get(0).get("content").contains("- a"));
  }

  @Test
  void updateLog_emptyWhenMissing() {
    BlueDockPublicProperties props = new BlueDockPublicProperties();
    props.getChangelog().setPath("/tmp/bluedock-missing-changelog-xyz.md");
    Map<String, Object> out = new SystemUpdateLogService(props).updateLog(20);
    assertEquals("", out.get("logVersion"));
    assertEquals("", out.get("updateLog"));
  }
}
