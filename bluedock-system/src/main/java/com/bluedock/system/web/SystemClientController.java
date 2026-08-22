package com.bluedock.system.web;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.model.ResultModel;
import com.bluedock.system.service.ChinaIpService;
import com.bluedock.system.service.SystemDemoService;
import com.bluedock.system.service.SystemUpdateLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemClientController {
  private final JdbcTemplate jdbc;
  private final ChinaIpService chinaIpService;
  private final SystemDemoService demoService;
  private final SystemUpdateLogService updateLogService;
  private final String version;

  public SystemClientController(
      JdbcTemplate jdbc,
      ChinaIpService chinaIpService,
      SystemDemoService demoService,
      SystemUpdateLogService updateLogService,
      @Value("${bluedock.version:1.0.0}") String version) {
    this.jdbc = jdbc;
    this.chinaIpService = chinaIpService;
    this.demoService = demoService;
    this.updateLogService = updateLogService;
    this.version = version;
  }

  @GetMapping("/version")
  public ResultModel<Map<String, Object>> version() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "BlueDock");
    data.put("version", version);
    data.put("publish", List.of());
    int deviceCount = 0;
    if (AuthContext.get() != null) {
      Integer n = jdbc.queryForObject(
          "SELECT COUNT(1) FROM bluedock_user_devices WHERE user_id = ? AND deleted_at IS NULL",
          Integer.class,
          AuthContext.get().userId());
      deviceCount = n == null ? 0 : n;
    }
    data.put("deviceCount", deviceCount);
    return ResultModel.ok(data);
  }

  @GetMapping("/prefetch")
  public ResultModel<List<String>> prefetch(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    List<String> list = new ArrayList<>();
    if (userAgent != null) {
      String userAgentLower = userAgent.toLowerCase(Locale.ROOT);
      if (userAgentLower.contains("bluedockdesk")
          || userAgentLower.contains("mainbluedockwindow")
          || userAgentLower.contains("maintaskwindow")
          || userAgentLower.contains("electron")) {
        // 桌面壳预加载清单可由运维写入 settings；首版返回空，保持契约形态
        list.addAll(List.of());
      }
    }
    return ResultModel.ok(list);
  }

  @GetMapping("/get/ip")
  public ResultModel<Map<String, Object>> ip(HttpServletRequest request) {
    return ResultModel.ok(Map.of("ip", ChinaIpService.clientIp(request)));
  }

  @GetMapping("/get/chinaIp")
  public ResultModel<Map<String, Object>> chinaIp(HttpServletRequest request) {
    String ip = ChinaIpService.clientIp(request);
    boolean isChina = chinaIpService.isChina(ip, request);
    return ResultModel.ok(Map.of("ip", ip, "isChina", isChina));
  }

  @GetMapping("/get/info")
  public ResultModel<Map<String, Object>> info() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "BlueDock");
    data.put("version", version);
    data.put("java", System.getProperty("java.version"));
    data.put("time", Instant.now().toString());
    return ResultModel.ok(data);
  }

  @GetMapping("/demo")
  public ResultModel<Map<String, Object>> demo() {
    return ResultModel.ok(demoService.demo());
  }

  @GetMapping("/get/updateLog")
  public ResultModel<Map<String, Object>> updateLog(@RequestParam(required = false) Integer take) {
    return ResultModel.ok(updateLogService.updateLog(take));
  }
}
