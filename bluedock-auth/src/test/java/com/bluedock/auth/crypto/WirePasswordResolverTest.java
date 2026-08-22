package com.bluedock.auth.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.AuthKeypair;
import com.bluedock.auth.repo.AuthKeypairRepository;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WirePasswordResolverTest {
  @Mock AuthKeypairRepository keypairs;

  WirePasswordResolver resolver;
  KeyPair keyPair;
  String keyId = "test-keyId";

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keyPair = generator.generateKeyPair();

    AuthKeypair entity = new AuthKeypair();
    entity.setKeyId(keyId);
    entity.setPrivateKeyEnc(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
    entity.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
    entity.setStatus("active");
    when(keypairs.findActive()).thenReturn(Optional.of(entity));

    resolver = new WirePasswordResolver(new RsaPasswordDecryptor(keypairs));
  }

  @Test
  void requirePlain_decrypts() throws Exception {
    String cipher = encrypt("Secret123!");
    assertEquals("Secret123!", resolver.requirePlain(keyId, cipher));
  }

  @Test
  void requirePlain_badKeyId() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> resolver.requirePlain("other", "YQ=="));
    assertEquals(ErrorCodes.PUBLIC_KEY_INVALID, ex.getCode());
  }

  @Test
  void requirePlain_missingKeyId() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> resolver.requirePlain("", "YQ=="));
    assertEquals(ErrorCodes.BAD_REQUEST, ex.getCode());
  }

  private String encrypt(String plain) throws Exception {
    OAEPParameterSpec oaep =
        new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaep);
    byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encrypted);
  }

  private static String toPem(String type, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
  }
}
