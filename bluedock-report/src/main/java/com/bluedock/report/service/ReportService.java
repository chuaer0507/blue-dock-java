package com.bluedock.report.service;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.report.ReportAiDraftBridge;
import com.bluedock.common.report.ReportDialogBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.report.domain.Report;
import com.bluedock.report.repo.ReportRepository;
import com.bluedock.report.web.dto.ReportView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private static final int SHARE_MAX = 20;

  private final ReportRepository reports;
  private final UserAccountRepository users;
  private final ReportDialogBridge dialogBridge;
  private final ReportAiDraftBridge aiDraftBridge;

  public ReportService(
      ReportRepository reports,
      UserAccountRepository users,
      @Autowired(required = false) ReportDialogBridge dialogBridge,
      @Autowired(required = false) ReportAiDraftBridge aiDraftBridge) {
    this.reports = reports;
    this.users = users;
    this.dialogBridge = dialogBridge;
    this.aiDraftBridge = aiDraftBridge;
  }

  public List<ReportView> my(String type, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    int[] p = pageOf(page, pageSize);
    return reports.listMine(userId, normalizeTypeFilter(type), p[0], p[1]).stream()
        .map(r -> ReportView.listItem(r, reports.listReceiveUserIds(r.getId())))
        .toList();
  }

  public List<ReportView> receive(String type, String status, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    int[] p = pageOf(page, pageSize);
    return reports.listReceived(userId, normalizeTypeFilter(type), status, p[0], p[1]).stream()
        .map(r -> ReportView.listItem(r, reports.listReceiveUserIds(r.getId())))
        .toList();
  }

  public ReportView detail(Long id, String code) {
    long userId = AuthContext.requireUserId();
    if (code != null && !code.isBlank()) {
      Map<String, Object> link =
          reports
              .findLinkByCode(code.trim())
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.REPORT_NOT_FOUND));
      long reportId = ((Number) link.get("reportId")).longValue();
      reports.incrementLinkOpenCount(code.trim());
      Report r =
          reports
              .findActive(reportId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.REPORT_NOT_FOUND));
      return ReportView.from(r, reports.listReceiveUserIds(reportId), analysisWire(reportId, userId));
    }
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_NOT_FOUND);
    }
    Report r = requireAccess(id, userId);
    return ReportView.from(r, reports.listReceiveUserIds(id), analysisWire(id, userId));
  }

  @Transactional
  public ReportView store(
      Long id, String title, String type, String content, String receive, Integer offset) {
    long userId = AuthContext.requireUserId();
    String t = title == null ? "" : title.trim();
    if (t.isEmpty() || t.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_TITLE_INVALID);
    }
    String ty = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    if (!"daily".equals(ty) && !"weekly".equals(ty)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_TYPE_INVALID);
    }
    String body = content == null ? "" : content.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_CONTENT_EMPTY);
    }
    List<Long> receivers = parseUserIds(receive);
    receivers.removeIf(u -> u == userId);
    if (receivers.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_RECEIVE_REQUIRED);
    }
    for (Long receiverUserId : receivers) {
      if (!users.existsByUserId(receiverUserId)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, receiverUserId);
      }
    }

    LocalDateTime now = LocalDateTime.now();
    Report report;
    if (id != null && id > 0) {
      report =
          reports
              .findActive(id)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.REPORT_NOT_FOUND));
      if (report.getUserId() != userId) {
        throw new BusinessException(ErrorCodes.REPORT_DENIED, I18nKeys.REPORT_DENIED);
      }
      report.setTitle(t);
      report.setType(ty);
      report.setContent(body);
      report.setUpdatedAt(now);
      reports.update(report);
      reports.deleteReceives(report.getId());
    } else {
      int off = offset == null ? 0 : offset;
      if (off > 0) {
        off = 0;
      }
      String sign = generateSign(ty, off);
      if (reports.existsSign(userId, ty, sign)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_DUPLICATE);
      }
      report = new Report();
      report.setId(IdGenerator.nextId());
      report.setSign(sign);
      report.setTitle(t);
      report.setType(ty);
      report.setUserId(userId);
      report.setContent(body);
      report.setCreatedAt(now);
      report.setUpdatedAt(now);
      reports.insert(report);
    }

    for (Long receiverUserId : receivers) {
      reports.insertReceive(IdGenerator.nextId(), report.getId(), receiverUserId, now);
    }
    return ReportView.from(report, receivers);
  }

  public Map<String, Object> template(String type, Integer offset) {
    long userId = AuthContext.requireUserId();
    String ty = type == null ? "daily" : type.trim().toLowerCase(Locale.ROOT);
    if (!"daily".equals(ty) && !"weekly".equals(ty)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_TYPE_INVALID);
    }
    int off = offset == null ? 0 : Math.min(0, offset);
    LocalDate anchor = periodAnchor(ty, off);
    String sign = generateSign(ty, off);
    String nickname =
        users
            .findByUserId(userId)
            .map(u -> u.getNickname() == null || u.getNickname().isBlank() ? u.getEmail() : u.getNickname())
            .orElse("User");

    LocalDateTime windowStart;
    LocalDateTime windowEnd;
    LocalDateTime nextStart = null;
    LocalDateTime nextEnd = null;
    String title;
    if ("daily".equals(ty)) {
      windowStart = anchor.atStartOfDay();
      windowEnd = anchor.atTime(23, 59, 59);
      title =
          nickname
              + "的日报["
              + anchor.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
              + "]";
    } else {
      LocalDate weekStart = anchor.with(WeekFields.ISO.dayOfWeek(), 1);
      LocalDate weekEnd = weekStart.plusDays(6);
      windowStart = weekStart.atStartOfDay();
      windowEnd = weekEnd.atTime(23, 59, 59);
      LocalDate nextWeekStart = weekStart.plusWeeks(1);
      nextStart = nextWeekStart.atStartOfDay();
      nextEnd = nextWeekStart.plusDays(6).atTime(23, 59, 59);
      int month = weekStart.getMonthValue();
      int weekOfMonth = weekStart.get(WeekFields.ISO.weekOfMonth());
      title =
          nickname
              + "的周报["
              + weekStart.format(DateTimeFormatter.ofPattern("M/d"))
              + "-"
              + weekEnd.format(DateTimeFormatter.ofPattern("M/d"))
              + "]["
              + month
              + "月第"
              + weekOfMonth
              + "周]";
    }

    List<String> completed = reports.listOwnerTaskNames(userId, windowStart, windowEnd, true);
    List<String> incomplete = reports.listOwnerTaskNames(userId, windowStart, windowEnd, false);
    List<String> nextWeek = List.of();
    if (nextStart != null) {
      nextWeek = reports.listOwnerTaskNames(userId, nextStart, nextEnd, false);
    }

    StringBuilder content = new StringBuilder();
    content.append("## 已完成工作\n");
    appendTaskBullets(content, completed);
    if ("daily".equals(ty)) {
      content.append("\n## 今日未完成\n");
      appendTaskBullets(content, incomplete);
    } else {
      content.append("\n## 本周未完成\n");
      appendTaskBullets(content, incomplete);
      content.append("\n## 下周拟定计划\n");
      appendTaskBullets(content, nextWeek);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("sign", sign);
    data.put("type", ty);
    data.put("title", title);
    data.put("content", content.toString().trim());
    data.put("completedCount", completed.size());
    data.put("incompleteCount", incomplete.size());
    if (!"daily".equals(ty)) {
      data.put("nextWeekCount", nextWeek.size());
    }
    return data;
  }

  private static void appendTaskBullets(StringBuilder out, List<String> names) {
    if (names == null || names.isEmpty()) {
      out.append("- （无）\n");
      return;
    }
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      out.append("- ").append(name.trim()).append('\n');
    }
  }

  private static LocalDate periodAnchor(String type, int offset) {
    if ("weekly".equals(type)) {
      return LocalDate.now().plusWeeks(offset);
    }
    return LocalDate.now().plusDays(offset);
  }

  private static String generateSign(String type, int offset) {
    LocalDate day = periodAnchor(type, offset);
    if ("weekly".equals(type)) {
      WeekFields wf = WeekFields.ISO;
      int week = day.get(wf.weekOfWeekBasedYear());
      int year = day.get(wf.weekBasedYear());
      return year + "-W" + String.format("%02d", week);
    }
    return day.format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  @Transactional
  public void mark(long id, int read) {
    long userId = AuthContext.requireUserId();
    if (!reports.isReceiver(id, userId)) {
      throw new BusinessException(ErrorCodes.REPORT_DENIED, I18nKeys.REPORT_DENIED);
    }
    reports.markRead(id, userId, read != 0);
  }

  @Transactional
  public void read(String ids) {
    long userId = AuthContext.requireUserId();
    List<Long> list = parseUserIds(ids);
    for (Long reportId : list) {
      if (reports.isReceiver(reportId, userId)) {
        reports.markRead(reportId, userId, true);
      }
    }
  }

  public Map<String, Object> unread() {
    long userId = AuthContext.requireUserId();
    return Map.of("unread", reports.countUnread(userId));
  }

  public Map<String, Object> lastSubmitter() {
    long userId = AuthContext.requireUserId();
    return reports
        .lastSubmitterReceive(userId)
        .map(submitterUserId -> Map.<String, Object>of("userId", submitterUserId))
        .orElse(Map.of());
  }

  /**
   * 分享报告到会话：生成/复用短码，向会话发送 Markdown 链接。
   *
   * @param refresh {@code yes} 时强制换新短码
   */
  @Transactional
  public Map<String, Object> share(String idRaw, String dialogRaw, String refresh) {
    long userId = AuthContext.requireUserId();
    List<Long> reportIds = parseUserIds(idRaw);
    List<Long> dialogIds = parseUserIds(dialogRaw);
    if (reportIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_NOT_FOUND);
    }
    if (dialogIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_DIALOG_REQUIRED);
    }
    if (reportIds.size() > SHARE_MAX
        || dialogIds.size() > SHARE_MAX
        || reportIds.size() * dialogIds.size() > SHARE_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_SHARE_LIMIT);
    }
    if (dialogBridge == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_SHARE_FAILED);
    }
    boolean forceRefresh = refresh != null && "yes".equalsIgnoreCase(refresh.trim());
    List<Map<String, Object>> items = new ArrayList<>();
    List<Long> messageIds = new ArrayList<>();
    for (Long reportId : reportIds) {
      Report report = requireAccess(reportId, userId);
      String code = ensureLinkCode(reportId, userId, forceRefresh);
      String url = "/single/report/detail/" + code;
      String text = "[" + report.getTitle() + "](" + url + ")";
      for (Long dialogId : dialogIds) {
        long messageId = dialogBridge.sendText(dialogId, text);
        if (messageId <= 0) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_SHARE_FAILED);
        }
        messageIds.add(messageId);
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", reportId);
      item.put("code", code);
      item.put("url", url);
      items.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", items);
    out.put("messageIds", messageIds);
    out.put("sharedCount", messageIds.size());
    return out;
  }

  /**
   * AI 整理汇报草稿：须已有正文；由 {@link ReportAiDraftBridge} 实现。
   */
  public Map<String, Object> aiGenerate(String type, String content) {
    String ty = type == null ? "daily" : type.trim().toLowerCase(Locale.ROOT);
    if (!"daily".equals(ty) && !"weekly".equals(ty)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_TYPE_INVALID);
    }
    String body = content == null ? "" : content.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_CONTENT_EMPTY);
    }
    if (aiDraftBridge == null || !aiDraftBridge.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_AI_UNAVAILABLE);
    }
    String polished = aiDraftBridge.polish(ty, body);
    if (polished == null || polished.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_AI_FAILED);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", ty);
    out.put("content", polished.trim());
    return out;
  }

  @Transactional
  public Map<String, Object> analysisSave(Map<String, Object> body) {
    long userId = AuthContext.requireUserId();
    Map<String, Object> b = body == null ? Map.of() : body;
    long reportId = asLong(b.get("id"));
    if (reportId <= 0) {
      reportId = asLong(b.get("reportId"));
    }
    if (reportId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_NOT_FOUND);
    }
    requireAccess(reportId, userId);
    String text = str(b.get("text"));
    if (text.isEmpty()) {
      text = str(b.get("analysisText"));
    }
    if (text.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_ANALYSIS_EMPTY);
    }
    String model = str(b.get("model"));
    String meta = null;
    Object metaObj = b.get("meta");
    if (metaObj != null) {
      meta = metaObj instanceof String s ? s : String.valueOf(metaObj);
    }
    LocalDateTime now = LocalDateTime.now();
    reports.upsertAnalysis(IdGenerator.nextId(), reportId, userId, text, model, meta, now);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", reportId);
    out.put("text", text);
    out.put("model", model);
    if (meta != null) {
      out.put("meta", meta);
    }
    return out;
  }

  private Map<String, Object> analysisWire(long reportId, long userId) {
    return reports
        .findAnalysis(reportId, userId)
        .map(
            row -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("text", row.getOrDefault("text", ""));
              m.put("model", row.getOrDefault("model", ""));
              Object meta = row.get("meta");
              if (meta != null && !String.valueOf(meta).isBlank()) {
                m.put("meta", meta);
              }
              return m;
            })
        .orElse(null);
  }

  private String ensureLinkCode(long reportId, long userId, boolean refresh) {
    LocalDateTime now = LocalDateTime.now();
    var exist = reports.findLinkByReportIdAndUserId(reportId, userId);
    if (exist.isPresent() && !refresh) {
      return String.valueOf(exist.get().get("code"));
    }
    String code = newLinkCode(reportId, userId);
    if (exist.isPresent()) {
      reports.updateLinkCode(((Number) exist.get().get("id")).longValue(), code, now);
    } else {
      reports.insertLink(IdGenerator.nextId(), reportId, userId, code, now);
    }
    return code;
  }

  private static String newLinkCode(long reportId, long userId) {
    String raw =
        reportId
            + ","
            + userId
            + ","
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Report requireAccess(long id, long userId) {
    Report r =
        reports
            .findActive(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.REPORT_NOT_FOUND));
    if (r.getUserId() != userId && !reports.isReceiver(id, userId)) {
      throw new BusinessException(ErrorCodes.REPORT_DENIED, I18nKeys.REPORT_DENIED);
    }
    return r;
  }

  private static String normalizeTypeFilter(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    String t = type.trim().toLowerCase(Locale.ROOT);
    if (!"daily".equals(t) && !"weekly".equals(t)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.REPORT_TYPE_INVALID);
    }
    return t;
  }

  private static int[] pageOf(Integer page, Integer pageSize) {
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(50, Math.max(1, pageSize));
    return new int[] {size, (p - 1) * size};
  }

  private static List<Long> parseUserIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return new ArrayList<>();
    }
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    String t = raw.trim();
    if (t.startsWith("[") && t.endsWith("]")) {
      t = t.substring(1, t.length() - 1);
    }
    for (String part : t.split("[,，;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        long id = Long.parseLong(part.trim());
        if (id > 0) {
          ids.add(id);
        }
      } catch (NumberFormatException ex) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, part);
      }
    }
    return new ArrayList<>(ids);
  }

  private static long asLong(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o != null) {
      try {
        return Long.parseLong(String.valueOf(o).trim());
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o).trim();
  }
}
