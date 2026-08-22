package com.bluedock.system.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.bluedock.system.config.SystemProperties;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 中国大陆 IP 判定：优先 CDN/网关国家头，其次可选 MaxMind MMDB，最后内网/本机启发式。
 */
@Service
public class ChinaIpService {
  private static final Logger log = LoggerFactory.getLogger(ChinaIpService.class);

  private static final String[] COUNTRY_HEADERS = {
    "CF-IPCountry",
    "CloudFront-Viewer-Country",
    "X-AppEngine-Country",
    "X-Country-Code",
    "X-Geo-Country"
  };

  private final SystemProperties props;
  private volatile DatabaseReader reader;
  private volatile String loadedPath = "";

  public ChinaIpService(SystemProperties props) {
    this.props = props;
  }

  public boolean isChina(String ip, HttpServletRequest request) {
    String headerCountry = countryFromHeaders(request);
    if (headerCountry != null) {
      return "CN".equalsIgnoreCase(headerCountry);
    }
    if (looksLikePrivateOrLocal(ip)) {
      return true;
    }
    String mmdbCountry = lookupCountry(ip);
    if (mmdbCountry != null) {
      return "CN".equalsIgnoreCase(mmdbCountry);
    }
    return false;
  }

  public static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    String real = request.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
  }

  static boolean looksLikePrivateOrLocal(String ip) {
    if (ip == null || ip.isBlank()) {
      return true;
    }
    String trimmed = ip.trim();
    if ("::1".equals(trimmed) || "https://example.net/id/garnet".equals(trimmed)) {
      return true;
    }
    if (trimmed.startsWith("127.") || trimmed.startsWith("10.") || trimmed.startsWith("192.168.")) {
      return true;
    }
    if (trimmed.startsWith("172.")) {
      String[] parts = trimmed.split("\\.");
      if (parts.length >= 2) {
        try {
          int second = Integer.parseInt(parts[1]);
          return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
          return false;
        }
      }
    }
    return false;
  }

  private static String countryFromHeaders(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    for (String name : COUNTRY_HEADERS) {
      String value = request.getHeader(name);
      if (value != null && !value.isBlank()) {
        String code = value.trim().toUpperCase(Locale.ROOT);
        if ("XX".equals(code) || "T1".equals(code)) {
          continue;
        }
        return code;
      }
    }
    return null;
  }

  private String lookupCountry(String ip) {
    DatabaseReader db = ensureReader();
    if (db == null || ip == null || ip.isBlank()) {
      return null;
    }
    try {
      InetAddress address = InetAddress.getByName(ip.trim());
      if (address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()) {
        return "CN";
      }
      CountryResponse response = db.country(address);
      if (response == null || response.getCountry() == null) {
        return null;
      }
      return response.getCountry().getIsoCode();
    } catch (IOException | GeoIp2Exception e) {
      log.debug("geoip lookup failed for {}: {}", ip, e.toString());
      return null;
    }
  }

  private DatabaseReader ensureReader() {
    String path = props.getGeoipMmdb() == null ? "" : props.getGeoipMmdb().trim();
    if (path.isEmpty()) {
      closeReader();
      return null;
    }
    DatabaseReader current = reader;
    if (current != null && path.equals(loadedPath)) {
      return current;
    }
    synchronized (this) {
      if (reader != null && path.equals(loadedPath)) {
        return reader;
      }
      closeReader();
      Path file = Path.of(path);
      if (!Files.isRegularFile(file)) {
        log.warn("geoip mmdb not found: {}", path);
        loadedPath = "";
        return null;
      }
      try {
        reader = new DatabaseReader.Builder(file.toFile()).build();
        loadedPath = path;
        log.info("geoip mmdb loaded: {}", path);
        return reader;
      } catch (IOException e) {
        log.warn("geoip mmdb open failed: {} {}", path, e.toString());
        loadedPath = "";
        return null;
      }
    }
  }

  @PreDestroy
  void destroy() {
    closeReader();
  }

  private void closeReader() {
    DatabaseReader current = reader;
    reader = null;
    loadedPath = "";
    if (current != null) {
      try {
        current.close();
      } catch (IOException ignored) {
        // ignore
      }
    }
  }
}
