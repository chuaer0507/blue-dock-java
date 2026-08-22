package com.bluedock.system.oss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bluedock.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class OssSettingsSupportTest {
  @Test
  void normalize_local_defaults() {
    OssSettingsDocument raw =
        new OssSettingsDocument(
            "local",
            null,
            null,
            null,
            null,
            "",
            "",
            new OssSettingsDocument.Local(""),
            null,
            null,
            null,
            null);
    OssSettingsDocument doc = OssSettingsSupport.normalize(raw, true);
    assertEquals("local", doc.provider());
    assertEquals(OssSettingsDocument.NAME_DATE_RANDOM, doc.nameType());
    assertTrue(doc.allowExtensions().contains("png"));
  }

  @Test
  void normalize_cloud_requiresDomain() {
    OssSettingsDocument raw =
        new OssSettingsDocument(
            "aliyun",
            null,
            null,
            OssSettingsDocument.DEFAULT_ALLOW_EXTENSIONS,
            "https",
            "",
            "",
            null,
            null,
            new OssSettingsDocument.Aliyun("ep", "ak", "sk", "b"),
            null,
            null);
    assertThrows(BusinessException.class, () -> OssSettingsSupport.normalize(raw, true));
  }

  @Test
  void assertExtensionAllowed() {
    OssSettingsSupport.assertExtensionAllowed("png,jpg", "a.PNG");
    assertThrows(
        BusinessException.class,
        () -> OssSettingsSupport.assertExtensionAllowed("png,jpg", "a.exe"));
  }

  @Test
  void maskAndMerge_keepSecret() {
    OssSettingsDocument existing =
        new OssSettingsDocument(
            "aliyun",
            OssSettingsDocument.NAME_DATE_RANDOM,
            OssSettingsDocument.LINK_SIMPLE,
            OssSettingsDocument.DEFAULT_ALLOW_EXTENSIONS,
            "https",
            "cdn.example.com",
            "https://cdn.example.com",
            null,
            null,
            new OssSettingsDocument.Aliyun("ep", "real-ak", "real-sk", "b"),
            null,
            null);
    OssSettingsDocument incoming =
        new OssSettingsDocument(
            "aliyun",
            null,
            null,
            null,
            "https",
            "cdn.example.com",
            null,
            null,
            null,
            new OssSettingsDocument.Aliyun("ep", OssSettingsDocument.MASK, "", "b2"),
            null,
            null);
    var merged = com.bluedock.system.service.OssSettingService.mergeSecrets(existing, incoming);
    assertEquals("real-ak", merged.aliyun().accessKeyId());
    assertEquals("real-sk", merged.aliyun().accessKeySecret());
    assertEquals("b2", merged.aliyun().bucket());
    var masked = com.bluedock.system.service.OssSettingService.maskSecrets(existing);
    assertEquals(OssSettingsDocument.MASK, masked.aliyun().accessKeyId());
  }
}
