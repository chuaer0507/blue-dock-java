package com.bluedock.auth.service;

import com.bluedock.auth.domain.AuthKeypair;
import com.bluedock.auth.repo.AuthKeypairRepository;
import com.bluedock.auth.web.dto.PublicKeyView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublicKeyService {
  private static final String ALGORITHM = "RSA-OAEP-SHA256";
  private static final Duration CACHE_TTL = Duration.ofHours(1);

  private final AuthKeypairRepository keypairs;
  private final StringRedisTemplate redis;

  public PublicKeyService(AuthKeypairRepository keypairs, StringRedisTemplate redis) {
    this.keypairs = keypairs;
    this.redis = redis;
  }

  public PublicKeyView getActivePublicKey() {
    AuthKeypair keypair =
        keypairs
            .findActive()
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_NO_ACTIVE_KEYPAIR));

    String cacheKey = RedisKeys.pubkey(keypair.getKeyId());
    String cached = redis.opsForValue().get(cacheKey);
    if (cached != null && !cached.isBlank()) {
      return new PublicKeyView(keypair.getKeyId(), cached, ALGORITHM);
    }

    String pem = keypair.getPublicKey();
    redis.opsForValue().set(cacheKey, pem, CACHE_TTL);
    return new PublicKeyView(keypair.getKeyId(), pem, ALGORITHM);
  }
}
