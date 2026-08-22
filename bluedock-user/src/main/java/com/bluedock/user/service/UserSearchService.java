package com.bluedock.user.service;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.user.web.dto.UserSearchView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 会员搜索 / AI 机器人列表。 */
@Service
public class UserSearchService {
  private final UserAccountRepository users;

  public UserSearchService(UserAccountRepository users) {
    this.users = users;
  }

  /**
   * 搜索会员。
   *
   * <p>{@code disable}：0 排除离职（默认）、1 含离职、2 仅离职；{@code isBot}：0 排除机器人（默认）、1
   * 含机器人、2 仅机器人。
   */
  public Map<String, Object> search(
      String key,
      Integer disable,
      Integer isBot,
      Long projectId,
      Long noProjectId,
      String nameAzSort,
      Integer take,
      Integer page,
      Integer pageSize) {
    AuthContext.requireUserId();
    int disableMode = disable == null ? 0 : disable;
    int botMode = isBot == null ? 0 : isBot;
    String keyword = key == null ? "" : key.trim();
    String az = normalizeAz(nameAzSort);

    boolean paged = page != null;
    int p = page == null || page < 1 ? 1 : page;
    int size;
    int offset;
    if (paged) {
      size = pageSize == null ? 10 : Math.min(100, Math.max(1, pageSize));
      offset = (p - 1) * size;
    } else {
      size = take == null ? 10 : Math.min(100, Math.max(1, take));
      offset = 0;
    }

    List<UserSearchView> list =
        users
            .search(keyword, disableMode, botMode, projectId, noProjectId, az, size, offset)
            .stream()
            .map(UserSearchView::from)
            .toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", list);
    if (paged) {
      out.put("total", users.countSearch(keyword, disableMode, botMode, projectId, noProjectId));
      out.put("page", p);
      out.put("pageSize", size);
    }
    return out;
  }

  /** 返回 AI 系统机器人（email 形如 {@code ai-*@bot.system}）。 */
  public Map<String, Object> searchAi(Integer take) {
    AuthContext.requireUserId();
    int limit = take == null ? 50 : Math.min(100, Math.max(1, take));
    List<UserSearchView> list =
        users.listAiBots(limit).stream().map(UserSearchView::from).toList();
    return Map.of("list", list);
  }

  private static String normalizeAz(String azSort) {
    if (azSort == null || azSort.isBlank()) {
      return "";
    }
    String v = azSort.trim().toLowerCase(Locale.ROOT);
    return "asc".equals(v) || "desc".equals(v) ? v : "";
  }
}
