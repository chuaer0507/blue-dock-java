package com.bluedock.boot.seed;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.boot.config.DeployEnvPathResolver;
import com.bluedock.common.deploy.CredentialGenerator;
import com.bluedock.common.deploy.DeployEnvWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次空库引导超级管理员（{@code bluedock_users.id=1}）。
 *
 * <p>随机邮箱（登录用户名）与密码，写入 {@code deploy/.env.dev} 或 {@code .env.prod}：
 * {@code #admin账号：} / {@code #admin密码：}。已有 id=1 则跳过，不覆盖凭据。
 */
@Component
@Order(10)
public class SuperAdminBootstrapRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

  private final String nickname;
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final DeployEnvPathResolver envPathResolver;

  public SuperAdminBootstrapRunner(
      @Value("${bluedock.seed.admin-nickname:Super Admin}") String nickname,
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      DeployEnvPathResolver envPathResolver) {
    this.nickname = nickname;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.envPathResolver = envPathResolver;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (users.existsByUserId(1L)) {
      return;
    }

    String email = CredentialGenerator.adminEmail();
    String password = CredentialGenerator.randomPassword(16);

    UserAccount admin = new UserAccount();
    admin.setUserId(1L);
    admin.setIdentity("[\"admin\"]");
    admin.setEmail(email);
    admin.setNickname(nickname);
    admin.setPassword(passwordEncoder.encode(password));
    admin.setIsBot(0);
    // 引导账号可直接登录；凭据已写入 .env，勿强制改密
    admin.setMustChangePassword(0);
    admin.setEmailVerify(1);
    users.insert(admin);

    Path envFile = envPathResolver.resolve();
    Map<String, String> credentials = new LinkedHashMap<>();
    credentials.put(DeployEnvWriter.ADMIN_USERNAME_PREFIX, email);
    credentials.put(DeployEnvWriter.ADMIN_PASSWORD_PREFIX, password);
    DeployEnvWriter.upsertCredentials(envFile, credentials);
    log.info(
        "Bootstrapped super admin user_id=1 email={} credentials written to {}",
        email,
        envFile.toAbsolutePath());
  }
}
