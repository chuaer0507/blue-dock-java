package com.bluedock.auth.crypto;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import org.springframework.stereotype.Component;

@Component
public class WirePasswordResolver {
  private final RsaPasswordDecryptor decryptor;

  public WirePasswordResolver(RsaPasswordDecryptor decryptor) {
    this.decryptor = decryptor;
  }

  public String requirePlain(String keyId, String cipher) {
    if (cipher == null || cipher.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_PASSWORD_REQUIRED);
    }
    if (keyId == null || keyId.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_KID_REQUIRED);
    }
    return decryptor.decrypt(keyId.trim(), cipher);
  }
}
