package com.bluedock.user.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.service.UserSearchService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {
  private final UserSearchService search;

  public UserSearchController(UserSearchService search) {
    this.search = search;
  }

  /**
   * 搜索会员。参数可扁平传递：{@code key}/{@code disable}/{@code isBot}/{@code projectId}/
   * {@code noProjectId}/{@code nameAz}/{@code take}|{@code page}+{@code pageSize}。
   */
  @GetMapping("/search")
  public ResultModel<Map<String, Object>> search(
      @RequestParam(required = false) String key,
      @RequestParam(required = false) String keys,
      @RequestParam(required = false) Integer disable,
      @RequestParam(required = false) Integer isBot,
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) Long noProjectId,
      @RequestParam(required = false) String nameAz,
      @RequestParam(required = false) Integer take,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    String kw = key != null && !key.isBlank() ? key : keys;
    return ResultModel.ok(
        search.search(
            kw, disable, isBot, projectId, noProjectId, nameAz, take, page, pageSize));
  }

  /** 获取 AI 系统机器人列表。 */
  @GetMapping("/search/ai")
  public ResultModel<Map<String, Object>> searchAi(
      @RequestParam(required = false) Integer take) {
    return ResultModel.ok(search.searchAi(take));
  }
}
