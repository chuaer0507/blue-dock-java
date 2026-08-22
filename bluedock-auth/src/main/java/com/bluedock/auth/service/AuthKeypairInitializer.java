package com.bluedock.auth.service;

import com.bluedock.auth.domain.AuthKeypair;
import com.bluedock.auth.repo.AuthKeypairRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 若无 active 密钥对，启动时生成 RSA-2048（dev / 首期）。 */
@Component
public class AuthKeypairInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AuthKeypairInitializer.class);
  private static final String ALGORITHM = "RSA-OAEP-SHA256";

  private final AuthKeypairRepository keypairs;

  public AuthKeypairInitializer(AuthKeypairRepository keypairs) {
    this.keypairs = keypairs;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (keypairs.findActive().isPresent()) {
      return;
    }

    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();

    String keyId = UUID.randomUUID().toString();
    AuthKeypair entity = new AuthKeypair();
    entity.setId(UUID.randomUUID().toString());
    entity.setKeyId(keyId);
    entity.setPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
    entity.setPrivateKeyEnc(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
    entity.setAlgorithm(ALGORITHM);
    entity.setStatus("active");
    entity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    keypairs.insert(entity);

    log.info("Generated initial RSA keypair keyId={}", keyId);
  }

  private static String toPem(String type, byte[] encoded) {
    String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
  }
}
