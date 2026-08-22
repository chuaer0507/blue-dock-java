package com.bluedock.messenger.share;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.user.UserShareDialogBridge;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.service.DialogService;
import com.bluedock.messenger.web.dto.DialogView;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UserShareDialogBridgeImpl implements UserShareDialogBridge {
  private final DialogRepository dialogs;
  private final DialogService dialogService;

  public UserShareDialogBridgeImpl(DialogRepository dialogs, DialogService dialogService) {
    this.dialogs = dialogs;
    this.dialogService = dialogService;
  }

  @Override
  public List<Map<String, Object>> listRecent(long userId, int take) {
    int n = Math.min(Math.max(take, 1), 100);
    List<Dialog> rows = dialogs.listForUser(userId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Dialog d : rows) {
      if (out.size() >= n) {
        break;
      }
      out.add(toItem(DialogView.from(d)));
    }
    return out;
  }

  @Override
  public List<Map<String, Object>> search(long userId, String key, int take) {
    int n = Math.min(Math.max(take, 1), 100);
    String q = key == null ? "" : key.trim();
    if (q.isEmpty()) {
      return listRecent(userId, n);
    }
    if (q.length() > 64) {
      q = q.substring(0, 64);
    }
    String like = "%" + escapeLike(q) + "%";
    List<Map<String, Object>> out = new ArrayList<>();
    for (Dialog d : dialogs.searchForUser(userId, like, n)) {
      out.add(toItem(DialogView.from(d)));
    }
    return out;
  }

  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  @Override
  public long ensureUserDialog(long me, long peerUserId) {
    AuthUser prev = AuthContext.get();
    try {
      AuthContext.set(new AuthUser(me));
      DialogView view = dialogService.openUser(peerUserId);
      return view == null ? 0L : view.id();
    } catch (RuntimeException e) {
      return 0L;
    } finally {
      if (prev == null) {
        AuthContext.clear();
      } else {
        AuthContext.set(prev);
      }
    }
  }

  private static Map<String, Object> toItem(DialogView d) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", d.id());
    row.put("name", d.name() == null ? "" : d.name());
    row.put("avatar", d.avatar() == null ? "" : d.avatar());
    row.put("type", d.type() == null ? "" : d.type());
    row.put("groupType", d.groupType() == null ? "" : d.groupType());
    long sort =
        d.lastAt() == null ? 0L : d.lastAt().toInstant(ZoneOffset.UTC).toEpochMilli() / 1000L;
    row.put("sort", sort);
    return row;
  }
}
