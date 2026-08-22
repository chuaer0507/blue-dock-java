package com.bluedock.system.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.config.BlueDockPublicProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 契约 {@code GET /api/system/demo}。 */
@Service
public class SystemDemoService {
  private final BlueDockPublicProperties props;

  public SystemDemoService(BlueDockPublicProperties props) {
    this.props = props;
  }

  public Map<String, Object> demo() {
    String account = props.getDemo().getAccount().trim();
    String password = props.getDemo().getPassword();
    if (account.isEmpty() || password == null || password.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.SYSTEM_DEMO_DISABLED);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("account", account);
    // 契约显式要求回显演示明文密码（非用户资料读接口）
    out.put("password", password);
    return out;
  }
}
