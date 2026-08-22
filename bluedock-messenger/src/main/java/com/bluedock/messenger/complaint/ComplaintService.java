package com.bluedock.messenger.complaint;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.system.service.AdminGuard;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话举报：成员提交；管理员列表 / 处理 / 删除。
 */
@Service
public class ComplaintService {
  public static final int STATUS_PENDING = 0;
  public static final int STATUS_HANDLED = 1;

  private static final Set<Integer> TYPES = Set.of(10, 20, 30, 40, 50, 60, 70);

  private final ComplaintRepository complaints;
  private final DialogRepository dialogs;
  private final AdminGuard adminGuard;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;

  public ComplaintService(
      ComplaintRepository complaints,
      DialogRepository dialogs,
      AdminGuard adminGuard,
      ObjectProvider<NotifySendPublisher> notifyPublisher) {
    this.complaints = complaints;
    this.dialogs = dialogs;
    this.adminGuard = adminGuard;
    this.notifyPublisher = notifyPublisher;
  }

  public Map<String, Object> lists(Integer type, Integer status, Integer page, Integer pageSize) {
    adminGuard.requireAdmin();
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 50 : Math.min(Math.max(pageSize, 1), 100);
    Integer typeFilter = type == null || type == 0 ? null : type;
    long total = complaints.count(typeFilter, status);
    List<ComplaintView> list =
        complaints.page(typeFilter, status, (p - 1) * size, size).stream()
            .map(ComplaintView::from)
            .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    out.put("page", p);
    out.put("pageSize", size);
    out.put("total", total);
    return out;
  }

  @Transactional
  public Map<String, Object> submit(
      long dialogId, int type, String reason, List<Map<String, Object>> images) {
    long userId = AuthContext.requireUserId();
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    if (!dialogs.isMember(dialogId, userId)) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.DIALOG_DENIED);
    }
    if (!TYPES.contains(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.COMPLAINT_TYPE);
    }
    String r = reason == null ? "" : reason.trim();
    if (r.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.COMPLAINT_REASON);
    }
    if (r.length() > 500) {
      r = r.substring(0, 500);
    }
    List<String> paths = new ArrayList<>();
    if (images != null) {
      for (Map<String, Object> img : images) {
        if (img == null) {
          continue;
        }
        Object path = img.get("path");
        if (path == null) {
          path = img.get("url");
        }
        if (path != null) {
          String p = String.valueOf(path).trim();
          if (!p.isEmpty()) {
            paths.add(p);
          }
        }
        if (paths.size() >= 9) {
          break;
        }
      }
    }
    LocalDateTime now = LocalDateTime.now();
    Complaint row = new Complaint();
    row.setId(IdGenerator.nextId());
    row.setDialogId(dialogId);
    row.setUserId(userId);
    row.setType(type);
    row.setReason(r);
    row.setImages(paths);
    row.setStatus(STATUS_PENDING);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    complaints.insert(row);
    notifyAdmins(r);
    return Map.of("ok", true, "id", row.getId());
  }

  @Transactional
  public Map<String, Object> action(long id, String type) {
    adminGuard.requireAdmin();
    String t = type == null ? "" : type.trim().toLowerCase();
    Complaint row =
        complaints
            .findById(id)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.COMPLAINT_NOT_FOUND));
    if ("handle".equals(t)) {
      complaints.updateStatus(row.getId(), STATUS_HANDLED);
      return Map.of("ok", true, "status", STATUS_HANDLED);
    }
    if ("delete".equals(t)) {
      complaints.deleteById(row.getId());
      return Map.of("ok", true, "deleted", true);
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.COMPLAINT_ACTION);
  }

  private void notifyAdmins(String reason) {
    NotifySendPublisher pub = notifyPublisher.getIfAvailable();
    if (pub == null) {
      return;
    }
    List<Long> admins = complaints.listRecentAdminIds(10);
    if (admins.isEmpty()) {
      return;
    }
    String title = "收到新的举报信息";
    String body = "收到新的举报信息：" + reason + " (请前往应用查看详情)";
    pub.publish(
        new NotifySendEvent(
            "complaint-" + IdGenerator.nextId(),
            NotifySendEvent.CHANNEL_DESKTOP,
            admins,
            title,
            body,
            Map.of("kind", "complaint")));
  }
}
