package com.bluedock.user.share;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.user.UserShareDialogBridge;
import com.bluedock.common.user.UserShareFileBridge;
import com.bluedock.user.web.dto.UserSearchView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 分享选择器：文件目录下钻 + 会话/用户候选。 */
@Service
public class UserShareListService {
  private static final int DIALOG_TAKE = 50;
  private static final String FOLDER_ICON = "/images/file/light/folder.png";
  private static final String FOLDER_SHARE_ICON = "/images/file/light/folder-share.png";

  private final ObjectProvider<UserShareFileBridge> files;
  private final ObjectProvider<UserShareDialogBridge> dialogs;
  private final UserAccountRepository users;

  public UserShareListService(
      ObjectProvider<UserShareFileBridge> files,
      ObjectProvider<UserShareDialogBridge> dialogs,
      UserAccountRepository users) {
    this.files = files;
    this.dialogs = dialogs;
    this.users = users;
  }

  /**
   * @param type {@code file}|{@code text}，默认 file
   * @param parentId 非空时下钻文件目录；空则返回根「文件」入口 + 会话候选
   */
  public List<Map<String, Object>> list(String type, String key, Long parentId) {
    long me = AuthContext.requireUserId();
    String shareType = normalizeType(type);
    if (parentId != null && "file".equals(shareType)) {
      return listFolders(me, parentId);
    }
    List<Map<String, Object>> lists = new ArrayList<>();
    if ("file".equals(shareType)) {
      lists.add(rootFileEntry());
    }
    lists.addAll(listDialogCandidates(me, shareType, key));
    return lists;
  }

  private List<Map<String, Object>> listFolders(long userId, long parentId) {
    UserShareFileBridge bridge = files.getIfAvailable();
    if (bridge == null) {
      return List.of();
    }
    List<Map<String, Object>> folders = bridge.listFolders(userId, parentId);
    List<Map<String, Object>> out = new ArrayList<>(folders.size());
    for (Map<String, Object> f : folders) {
      long id = asLong(f.get("id"));
      boolean shared = Boolean.TRUE.equals(f.get("isShared"));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("type", "children");
      row.put("url", "/api/users/share/list?parentId=" + id);
      row.put("icon", shared ? FOLDER_SHARE_ICON : FOLDER_ICON);
      row.put("name", String.valueOf(f.getOrDefault("name", "")));
      row.put("extend", Map.of("uploadFileId", id));
      out.add(row);
    }
    return out;
  }

  private Map<String, Object> rootFileEntry() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("type", "children");
    row.put("url", "/api/users/share/list?parentId=0");
    row.put("icon", FOLDER_ICON);
    row.put("extend", Map.of("uploadFileId", 0L));
    row.put("name", Messages.get(I18nKeys.USER_SHARE_FILES));
    row.put("sort", 253402300799L); // 远未来，置顶
    return row;
  }

  private List<Map<String, Object>> listDialogCandidates(long me, String shareType, String key) {
    UserShareDialogBridge bridge = dialogs.getIfAvailable();
    if (bridge == null) {
      return List.of();
    }
    String q = key == null ? "" : key.trim();
    List<Map<String, Object>> dialogRows =
        q.isEmpty() ? bridge.listRecent(me, DIALOG_TAKE) : bridge.search(me, q, DIALOG_TAKE);
    String itemUrl =
        "file".equals(shareType)
            ? "/api/dialog/message/sendFiles"
            : "/api/dialog/message/sendText";
    Set<Long> dialogIds = new HashSet<>();
    List<Map<String, Object>> lists = new ArrayList<>();
    for (Map<String, Object> d : dialogRows) {
      long id = asLong(d.get("id"));
      if (id <= 0 || !dialogIds.add(id)) {
        continue;
      }
      lists.add(dialogItem(d, itemUrl, asLong(d.get("sort"))));
    }
    if (!q.isEmpty() && dialogRows.size() < DIALOG_TAKE) {
      int remain = DIALOG_TAKE - lists.size();
      if (remain > 0) {
        List<UserAccount> found =
            users.search(q, 0, 0, null, null, "", remain, 0);
        for (UserAccount u : found) {
          if (u.getUserId() == me) {
            continue;
          }
          long dialogId = bridge.ensureUserDialog(me, u.getUserId());
          if (dialogId <= 0 || !dialogIds.add(dialogId)) {
            continue;
          }
          UserSearchView view = UserSearchView.from(u);
          Map<String, Object> fake = new LinkedHashMap<>();
          fake.put("id", dialogId);
          fake.put("name", view.nickname());
          fake.put("avatar", view.userImage());
          fake.put("type", "user");
          fake.put("groupType", "");
          lists.add(dialogItem(fake, itemUrl, 0L));
        }
        lists.sort(
            Comparator.comparingLong(
                    (Map<String, Object> m) -> asLong(m.get("sort")))
                .reversed());
      }
    }
    return lists;
  }

  private static Map<String, Object> dialogItem(
      Map<String, Object> d, String itemUrl, long sort) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("type", "item");
    row.put("name", String.valueOf(d.getOrDefault("name", "")));
    row.put("icon", String.valueOf(d.getOrDefault("avatar", "")));
    row.put("url", itemUrl);
    row.put("sort", sort);
    Map<String, Object> extend = new LinkedHashMap<>();
    extend.put("dialogIds", asLong(d.get("id")));
    extend.put("textType", "text");
    extend.put("replyId", 0);
    extend.put("silence", "no");
    row.put("extend", extend);
    return row;
  }

  private static String normalizeType(String type) {
    if (type == null || type.isBlank()) {
      return "file";
    }
    String t = type.trim().toLowerCase(Locale.ROOT);
    return "text".equals(t) ? "text" : "file";
  }

  private static long asLong(Object v) {
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }
}
