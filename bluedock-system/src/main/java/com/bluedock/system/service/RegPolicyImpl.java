package com.bluedock.system.service;

import com.bluedock.common.auth.RegPolicy;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RegPolicyImpl implements RegPolicy {
  private final SystemGeneralSettingService general;
  private final EmailSettingService email;

  public RegPolicyImpl(SystemGeneralSettingService general, EmailSettingService email) {
    this.general = general;
    this.email = email;
  }

  @Override
  public boolean needInvite() {
    return "invite".equalsIgnoreCase(regMode());
  }

  @Override
  public boolean isRegistrationClosed() {
    return "close".equalsIgnoreCase(regMode());
  }

  @Override
  public void assertInvite(String invite) {
    if (!needInvite()) {
      return;
    }
    String expect = str(general.loadRaw().get("inviteCode"));
    String got = invite == null ? "" : invite.trim();
    if (expect.isEmpty() || !expect.equals(got)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_INVITE_INVALID);
    }
  }

  @Override
  public boolean isRegVerifyOpen() {
    Map<String, Object> m = email.loadRaw();
    Object v = m.get("regVerify");
    return v != null && "open".equalsIgnoreCase(String.valueOf(v).trim());
  }

  private String regMode() {
    Object v = general.loadRaw().get("reg");
    return v == null ? "invite" : String.valueOf(v).trim();
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
