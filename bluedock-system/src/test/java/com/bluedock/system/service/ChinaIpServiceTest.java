package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bluedock.system.config.SystemProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChinaIpServiceTest {
  private ChinaIpService service;

  @BeforeEach
  void setUp() {
    SystemProperties props = new SystemProperties();
    props.setGeoipMmdb("");
    service = new ChinaIpService(props);
  }

  @Test
  void headerCn_isChina() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("CF-IPCountry")).thenReturn("CN");
    assertTrue(service.isChina("8.8.8.8", request));
  }

  @Test
  void headerUs_notChina() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("CF-IPCountry")).thenReturn("US");
    assertFalse(service.isChina("8.8.8.8", request));
  }

  @Test
  void cloudFrontHeader_preferredWhenCfMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("CF-IPCountry")).thenReturn(null);
    when(request.getHeader("CloudFront-Viewer-Country")).thenReturn("cn");
    assertTrue(service.isChina("1.2.3.4", request));
  }

  @Test
  void privateIp_withoutHeader_isChina() {
    assertTrue(service.isChina("192.168.1.1", mock(HttpServletRequest.class)));
    assertTrue(service.isChina("10.0.0.2", mock(HttpServletRequest.class)));
    assertTrue(service.isChina("172.16.0.1", mock(HttpServletRequest.class)));
    assertTrue(service.isChina("127.0.0.1", mock(HttpServletRequest.class)));
  }

  @Test
  void publicIp_withoutHeaderOrMmdb_notChina() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("CF-IPCountry")).thenReturn(null);
    when(request.getHeader("CloudFront-Viewer-Country")).thenReturn(null);
    when(request.getHeader("X-AppEngine-Country")).thenReturn(null);
    when(request.getHeader("X-Country-Code")).thenReturn(null);
    when(request.getHeader("X-Geo-Country")).thenReturn(null);
    assertFalse(service.isChina("8.8.8.8", request));
  }

  @Test
  void looksLikePrivateOrLocal_coversRfc1918() {
    assertTrue(ChinaIpService.looksLikePrivateOrLocal("172.31.255.1"));
    assertFalse(ChinaIpService.looksLikePrivateOrLocal("172.15.0.1"));
    assertFalse(ChinaIpService.looksLikePrivateOrLocal("172.32.0.1"));
  }
}
