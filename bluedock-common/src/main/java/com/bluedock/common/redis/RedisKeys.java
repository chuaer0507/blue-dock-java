package com.bluedock.common.redis;

/** Redis Key 常量；禁止业务代码硬编码 Key 字符串。 */
public final class RedisKeys {
  private static final String PREFIX = "bluedock";

  private RedisKeys() {}

  public static String captcha(String id) {
    return PREFIX + ":auth:captcha:" + id;
  }

  /** 登录失败计数（按客户端 IP）。 */
  public static String loginFail(String ip) {
    return PREFIX + ":auth:login:fail:" + ip;
  }

  /** 活跃公钥 PEM 缓存。 */
  public static String pubkey(String keyId) {
    return PREFIX + ":auth:pubkey:" + keyId;
  }

  public static String blacklist(String jti) {
    return PREFIX + ":auth:blacklist:" + jti;
  }

  public static String qrCode(String code) {
    return PREFIX + ":auth:qrCode:" + code;
  }

  public static String online(long userId) {
    return PREFIX + ":online:" + userId;
  }

  public static String pcActive(long userId) {
    return PREFIX + ":pc:active:" + userId;
  }

  public static String upload(String uploadId) {
    return PREFIX + ":upload:" + uploadId;
  }

  public static String uploadChunks(String uploadId) {
    return PREFIX + ":upload:" + uploadId + ":chunks";
  }

  public static String wsUser(long userId) {
    return PREFIX + ":ws:user:" + userId;
  }

  public static String wsSession(String sessionId) {
    return PREFIX + ":ws:session:" + sessionId;
  }

  public static String lockProject(long projectId) {
    return PREFIX + ":lock:project:" + projectId;
  }

  public static String lockFileDir(long userId, long parentId) {
    return PREFIX + ":lock:file:dir:" + userId + ":" + parentId;
  }

  public static String rate(long userId, String action) {
    return PREFIX + ":rate:" + userId + ":" + action;
  }

  public static String setting(String group) {
    return PREFIX + ":setting:" + group;
  }

  public static String accessToken(String token) {
    return PREFIX + ":auth:token:" + token;
  }

  /** token MD5 → 原始 token，供设备登出吊销。 */
  public static String accessTokenHash(String hash) {
    return PREFIX + ":auth:token-hash:" + hash;
  }

  /** refreshToken → {@code userId|accessToken}，供无感续期与连带吊销。 */
  public static String refreshToken(String token) {
    return PREFIX + ":auth:refresh:" + token;
  }

  /** accessToken → refreshToken，登出时连带吊销 refresh。 */
  public static String accessToRefresh(String accessToken) {
    return PREFIX + ":auth:access-refresh:" + accessToken;
  }

  /** 注册 / 重置密码邮箱 OTP：{@code purpose}=reg|reset。 */
  public static String authEmailCode(String purpose, String email) {
    return PREFIX + ":auth:email:code:" + purpose + ":" + email;
  }

  /** 邮箱 OTP 发送冷却。 */
  public static String authEmailCodeCool(String purpose, String email) {
    return PREFIX + ":auth:email:cool:" + purpose + ":" + email;
  }

  /** 客户端加密公钥对应私钥缓存。 */
  public static String clientKeyPair(String clientId) {
    return PREFIX + ":auth:client-key:" + clientId;
  }

  public static String meetingShare(String code) {
    return PREFIX + ":meeting:share:" + code;
  }

  public static String meetingTourist(String touristId) {
    return PREFIX + ":meeting:tourist:" + touristId;
  }

  /** 关房调度节流（约 10 分钟）。 */
  public static String meetingCloseTick() {
    return PREFIX + ":meeting:close:tick";
  }

  /** 未读邮件汇总调度互斥（约 4 分钟）。 */
  public static String emailUnreadNoticeTick() {
    return PREFIX + ":email:unread:notice:tick";
  }

  /** 会话打开 webhook 节流（约 1 分钟）。 */
  public static String userBotDialogOpen(long dialogId, long userId) {
    return PREFIX + ":userBot:dialogOpen:" + dialogId + ":" + userId;
  }

  public static String notifyIdempotency(String eventId) {
    return PREFIX + ":notify:idempotency:" + eventId;
  }

  /** APP 推送 PC 在线延时调度（ZSET score=到期 epoch ms；非业务 MQ）。 */
  public static String appPushDelayQueue() {
    return PREFIX + ":appPush:delay:queue";
  }

  /** 延时任务载荷 JSON。 */
  public static String appPushDelayJob(String jobId) {
    return PREFIX + ":appPush:delay:job:" + jobId;
  }

  /** 延时队列轮询互斥。 */
  public static String appPushDelayTick() {
    return PREFIX + ":appPush:delay:tick";
  }

  public static String searchIndexIdempotency(String eventId) {
    return PREFIX + ":search:idempotency:" + eventId;
  }

  /** 全量重建互斥锁。 */
  public static String searchRebuildLock() {
    return PREFIX + ":search:rebuild:lock";
  }

  /** 全量重建进度 JSON。 */
  public static String searchRebuildStatus() {
    return PREFIX + ":search:rebuild:status";
  }

  public static String userBotWebhookIdempotency(String eventId) {
    return PREFIX + ":userBot:webhook:idempotency:" + eventId;
  }

  public static String userBotWebhookReplyIdempotency(String eventId) {
    return PREFIX + ":userBot:webhook:reply:idempotency:" + eventId;
  }

  /** AI 助手流式会话凭证（短时）。 */
  public static String assistantStream(String streamKey) {
    return PREFIX + ":assistant:stream:" + streamKey;
  }

  /** AI 操作结果（取走即删）。 */
  public static String assistantOp(String requestId) {
    return PREFIX + ":ai:op:" + requestId;
  }

  /** 文件打包任务元数据 JSON（path/userId/name/size）。 */
  public static String filePack(String packId) {
    return PREFIX + ":file:pack:" + packId;
  }

  /** OnlyOffice 编辑会话。 */
  public static String officeToken(String token) {
    return PREFIX + ":file:office:" + token;
  }

  /** 导出下载票据 JSON（path/userId/name/size）。 */
  public static String exportDown(String key) {
    return PREFIX + ":export:down:" + key;
  }

  public static String exportIdempotency(String eventId) {
    return PREFIX + ":export:idempotency:" + eventId;
  }

  /** 导出 system-msg 私聊投递幂等。 */
  public static String exportNotifyIdempotency(String eventId) {
    return PREFIX + ":export:notify:idempotency:" + eventId;
  }

  /** 对话 AI 会话标题已生成标记。 */
  public static String dialogSessionTitleDone(long dialogId, long userId, String sessionKey) {
    return PREFIX + ":dialog:sessionTitle:done:" + dialogId + ":" + userId + ":" + sessionKey;
  }

  /** Draw.io 图标搜索缓存。 */
  public static String drawioIconSearch(String cacheKey) {
    return PREFIX + ":drawio:iconSearch:" + cacheKey;
  }

  /** 任务 AI 自动扫描调度互斥。 */
  public static String taskAiScanTick() {
    return PREFIX + ":task:aiScan:tick";
  }

  /** 未领取任务提醒当日幂等。 */
  public static String unclaimedTaskRemindSent(String day) {
    return PREFIX + ":task:unclaimedRemind:sent:" + day;
  }

  /** 未领取任务提醒调度互斥。 */
  public static String unclaimedTaskRemindTick() {
    return PREFIX + ":task:unclaimedRemind:tick";
  }

  /** 任务自动归档调度互斥。 */
  public static String taskAutoArchiveTick() {
    return PREFIX + ":task:autoArchive:tick";
  }

  /** 消息待办到期提醒调度互斥。 */
  public static String dialogTodoRemindTick() {
    return PREFIX + ":dialog:todoRemind:tick";
  }

  /** 消息待办到期提醒幂等。 */
  public static String dialogTodoRemindSent(long todoId) {
    return PREFIX + ":dialog:todoRemind:sent:" + todoId;
  }

  /** 机器人 clearDay 清理调度互斥。 */
  public static String userBotClearDayTick() {
    return PREFIX + ":userBot:clearDay:tick";
  }

  /** 签到提醒调度互斥（约 50 秒）。 */
  public static String attendanceRemindTick() {
    return PREFIX + ":attendance:remind:tick";
  }

  /**
   * 签到提醒当日幂等：{@code kind}=in|exceed。
   */
  public static String attendanceRemindSent(String day, long userId, String kind) {
    return PREFIX + ":attendance:remind:sent:" + day + ":" + userId + ":" + kind;
  }

  /** 在线授权邮箱验证码。 */
  public static String licenseOnlineCode(String email) {
    return PREFIX + ":license:online:code:" + email.toLowerCase();
  }

  /** 在线授权登录待确认会话。 */
  public static String licenseOnlinePending(String token) {
    return PREFIX + ":license:online:pending:" + token;
  }

  /** 本机 SN 已领取试用标记。 */
  public static String licenseOnlineTrial(String sn) {
    return PREFIX + ":license:online:trial:" + sn;
  }
}
