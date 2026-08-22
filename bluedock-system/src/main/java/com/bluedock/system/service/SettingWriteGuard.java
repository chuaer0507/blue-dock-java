package com.bluedock.system.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 演示环境 {@code SYSTEM_SETTING=disabled} 时禁止写入系统设置。 */
@Component
public class SettingWriteGuard {
  private final Environment env;

  public SettingWriteGuard(Environment env) {
    this.env = env;
  }

  public void requireWritable() {
    String v = env.getProperty("SYSTEM_SETTING", env.getProperty("system.setting", "open"));
    if (v != null && "disabled".equalsIgnoreCase(v.trim())) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.SYSTEM_SETTING_DISABLED);
    }
  }

  public boolean isDisabled() {
    String v = env.getProperty("SYSTEM_SETTING", env.getProperty("system.setting", "open"));
    return v != null && "disabled".equalsIgnoreCase(v.trim());
  }
}
