package com.bluedock.auth.web;

import com.bluedock.auth.security.BearerTokens;
import com.bluedock.auth.service.AuthService;
import com.bluedock.auth.service.CaptchaService;
import com.bluedock.auth.service.PublicKeyService;
import com.bluedock.auth.service.QrCodeLoginService;
import com.bluedock.auth.service.RegisterService;
import java.util.Map;
import com.bluedock.auth.web.dto.CaptchaJsonView;
import com.bluedock.auth.web.dto.LoginRequest;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.auth.web.dto.NeedCodeView;
import com.bluedock.auth.web.dto.PublicKeyView;
import com.bluedock.auth.web.dto.RefreshResult;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.model.ResultModel;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class AuthController {
  private static final String CAPTCHA_COOKIE = "bluedock_captcha_key";

  private final AuthService authService;
  private final PublicKeyService publicKeys;
  private final CaptchaService captcha;
  private final QrCodeLoginService qrCode;
  private final RegisterService registerService;

  public AuthController(
      AuthService authService,
      PublicKeyService publicKeys,
      CaptchaService captcha,
      QrCodeLoginService qrCode,
      RegisterService registerService) {
    this.authService = authService;
    this.publicKeys = publicKeys;
    this.captcha = captcha;
    this.qrCode = qrCode;
    this.registerService = registerService;
  }

  @GetMapping("/key/client")
  public ResultModel<PublicKeyView> clientKey() {
    return ResultModel.ok(publicKeys.getActivePublicKey());
  }

  @GetMapping("/login/needCode")
  public ResultModel<NeedCodeView> needCode(HttpServletRequest request) {
    return ResultModel.ok(authService.needCode(clientIp(request)));
  }

  /** 注册是否需要邀请码：`systemSetting.reg == invite`。 */
  @GetMapping("/register/needInvite")
  public ResultModel<NeedCodeView> needInvite() {
    return ResultModel.ok(authService.needInvite());
  }

  @GetMapping("/login/codeJson")
  public ResultModel<CaptchaJsonView> codeJson() {
    return ResultModel.ok(captcha.createJson());
  }

  @GetMapping("/login/codeImage")
  public ResponseEntity<byte[]> codeImage(HttpServletResponse response) {
    CaptchaService.Issued issued = captcha.createImage();
    Cookie cookie = new Cookie(CAPTCHA_COOKIE, issued.key());
    cookie.setPath("/");
    cookie.setMaxAge(300);
    cookie.setHttpOnly(true);
    response.addCookie(cookie);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Captcha-Key", issued.key())
        .contentType(MediaType.IMAGE_PNG)
        .body(issued.pngBytes());
  }

  /**
   * 登录：GET 用 query；POST 推荐 JSON body（{@code application/json}），亦兼容 form/query。
   * password 为 RSA 密文，须带 keyId。
   */
  @GetMapping("/login")
  public ResultModel<LoginResult> loginGet(
      @RequestParam String email,
      @RequestParam String password,
      @RequestParam String keyId,
      @RequestParam(required = false) String captchaKey,
      @RequestParam(required = false) String captchaCode,
      @RequestParam(required = false) String codeKey,
      @RequestParam(required = false) String code,
      HttpServletRequest request) {
    return login(
        email, password, keyId, captchaKey, captchaCode, codeKey, code, request);
  }

  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResultModel<LoginResult> loginPostJson(
      @RequestBody LoginRequest body, HttpServletRequest request) {
    LoginRequest req = body == null ? new LoginRequest(null, null, null, null, null, null, null) : body;
    return login(
        req.email(),
        req.password(),
        req.keyId(),
        req.captchaKey(),
        req.captchaCode(),
        req.codeKey(),
        req.code(),
        request);
  }

  @PostMapping("/login")
  public ResultModel<LoginResult> loginPost(
      @RequestParam String email,
      @RequestParam String password,
      @RequestParam String keyId,
      @RequestParam(required = false) String captchaKey,
      @RequestParam(required = false) String captchaCode,
      @RequestParam(required = false) String codeKey,
      @RequestParam(required = false) String code,
      HttpServletRequest request) {
    return login(
        email, password, keyId, captchaKey, captchaCode, codeKey, code, request);
  }

  private ResultModel<LoginResult> login(
      String email,
      String password,
      String keyId,
      String captchaKey,
      String captchaCode,
      String codeKey,
      String code,
      HttpServletRequest request) {
    return ResultModel.ok(
        authService.login(
            email,
            password,
            keyId,
            clientIp(request),
            request.getHeader("User-Agent"),
            firstNonBlank(captchaKey, codeKey, cookieValue(request, CAPTCHA_COOKIE)),
            firstNonBlank(captchaCode, code)));
  }

  /**
   * 扫码登录。{@code type}=create|confirm|status；confirm 需 Bearer；status 成功返回新 token。
   */
  @GetMapping("/login/qrCode")
  public ResultModel<Map<String, Object>> loginQrCode(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String code,
      HttpServletRequest request) {
    return ResultModel.ok(
        qrCode.handle(type, code, clientIp(request), request.getHeader("User-Agent")));
  }

  @GetMapping("/info")
  public ResultModel<UserPublicView> info() {
    return ResultModel.ok(authService.currentProfile());
  }

  /** 查询当前 Bearer token 剩余有效期。 */
  @GetMapping("/token/expire")
  public ResultModel<Map<String, Object>> tokenExpire(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ResultModel.ok(authService.tokenExpire(BearerTokens.extract(authorization)));
  }

  /**
   * 无感续期：用 refreshToken 轮换新的 access + refresh。
   * 匿名；失败 {@code code=-2}。
   */
  @PostMapping("/token/refresh")
  public ResultModel<RefreshResult> refreshPost(@RequestParam String refreshToken) {
    return ResultModel.ok(authService.refresh(refreshToken));
  }

  @GetMapping("/token/refresh")
  public ResultModel<RefreshResult> refreshGet(@RequestParam String refreshToken) {
    return ResultModel.ok(authService.refresh(refreshToken));
  }

  /** 发送邮箱 OTP：{@code type}=reg|reset；SMTP 未配时回 {@code devCode}。 */
  @GetMapping("/email/code")
  public ResultModel<Map<String, Object>> emailCode(
      @RequestParam String email, @RequestParam String type) {
    return ResultModel.ok(registerService.sendEmailCode(email, type));
  }

  /** 自助注册：RSA 密码 + 邮箱验证码；可选邀请码。成功可直接带回 token。 */
  @PostMapping("/register")
  public ResultModel<Map<String, Object>> register(
      @RequestParam String email,
      @RequestParam String password,
      @RequestParam String keyId,
      @RequestParam String emailCode,
      @RequestParam(required = false) String nickname,
      @RequestParam(required = false) String invite,
      HttpServletRequest request) {
    return ResultModel.ok(
        registerService.register(
            email,
            password,
            keyId,
            emailCode,
            nickname,
            invite,
            clientIp(request),
            request.getHeader("User-Agent")));
  }

  /** 忘记密码：校验邮箱 OTP 后写入 RSA 新密码。 */
  @PostMapping("/password/reset")
  public ResultModel<Map<String, Object>> passwordReset(
      @RequestParam String email,
      @RequestParam String emailCode,
      @RequestParam String password,
      @RequestParam String keyId) {
    return ResultModel.ok(registerService.resetPassword(email, emailCode, password, keyId));
  }

  @GetMapping("/logout")
  public ResultModel<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    authService.logout(BearerTokens.extract(authorization));
    return ResultModel.ok();
  }

  private static String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
