package com.bluedock.boot.seed;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.user.bot.SystemUserBots;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 种子系统机器人（email=@bot.system）。 */
@Component
@Order(20)
public class SeedSystemUserBotsRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SeedSystemUserBotsRunner.class);

  private final boolean enabled;
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;

  public SeedSystemUserBotsRunner(
      @Value("${bluedock.seed.enabled:false}") boolean enabled,
      UserAccountRepository users,
      PasswordEncoder passwordEncoder) {
    this.enabled = enabled;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    int n = 0;
    for (Map.Entry<String, String> e : SystemUserBots.all().entrySet()) {
      String email = SystemUserBots.emailOf(e.getKey());
      if (users.findByEmail(email).isPresent()) {
        continue;
      }
      UserAccount bot = new UserAccount();
      bot.setUserId(IdGenerator.nextId());
      bot.setEmail(email);
      bot.setNickname(e.getValue());
      bot.setUserImage("");
      bot.setIdentity("[]");
      bot.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
      bot.setIsBot(1);
      users.insert(bot);
      n++;
    }
    if (n > 0) {
      log.info("Seeded {} system bots", n);
    }
  }
}
