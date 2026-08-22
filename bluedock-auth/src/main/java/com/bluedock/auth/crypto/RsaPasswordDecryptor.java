package com.bluedock.auth.crypto;

import com.bluedock.auth.domain.AuthKeypair;
import com.bluedock.auth.repo.AuthKeypairRepository;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.stereotype.Component;

@Component
public class RsaPasswordDecryptor {
  private static final OAEPParameterSpec OAEP_SHA256 =
      new OAEPParameterSpec(
          "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

  private final AuthKeypairRepository keypairs;

  public RsaPasswordDecryptor(AuthKeypairRepository keypairs) {
    this.keypairs = keypairs;
  }

  public String decrypt(String keyId, String base64Cipher) {
    AuthKeypair keypair =
        keypairs
            .findActive()
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodes.PUBLIC_KEY_INVALID, I18nKeys.AUTH_NO_ACTIVE_KEYPAIR));
    if (!keypair.getKeyId().equals(keyId)) {
      throw new BusinessException(
          ErrorCodes.PUBLIC_KEY_INVALID, I18nKeys.AUTH_PUBLIC_KEY_INVALID);
    }
    try {
      PrivateKey privateKey = loadPrivateKey(keypair.getPrivateKeyEnc());
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
      byte[] plain =
          cipher.doFinal(Base64.getDecoder().decode(base64Cipher.replaceAll("\\s", "")));
      return new String(plain, StandardCharsets.UTF_8);
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(
          ErrorCodes.PUBLIC_KEY_INVALID, I18nKeys.AUTH_PUBLIC_KEY_INVALID);
    }
  }

  private static PrivateKey loadPrivateKey(String pem) throws Exception {
    String stripped =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] encoded = Base64.getDecoder().decode(stripped);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
  }
}
