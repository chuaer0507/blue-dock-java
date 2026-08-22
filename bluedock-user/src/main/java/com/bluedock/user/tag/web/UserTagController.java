package com.bluedock.user.tag.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.tag.service.UserTagService;
import com.bluedock.user.tag.web.dto.UserTagView;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/tags")
public class UserTagController {
  private final UserTagService tags;

  public UserTagController(UserTagService tags) {
    this.tags = tags;
  }

  /** 个性标签列表；{@code userId} 缺省为当前用户。 */
  @GetMapping("/lists")
  public ResultModel<Map<String, Object>> lists(@RequestParam(required = false) Long userId) {
    return ResultModel.ok(tags.lists(userId));
  }

  /** 新增个性标签；{@code userId} 为被贴标签用户，缺省为自己。 */
  @PostMapping("/add")
  public ResultModel<UserTagView> add(
      @RequestParam(required = false) Long userId, @RequestParam String name) {
    return ResultModel.ok(tags.add(userId, name));
  }

  /** 修改自己创建的个性标签名称。 */
  @PostMapping("/update")
  public ResultModel<UserTagView> update(@RequestParam Long id, @RequestParam String name) {
    return ResultModel.ok(tags.update(id, name));
  }

  /** 删除标签；创建者或系统管理员。 */
  @PostMapping("/delete")
  public ResultModel<Map<String, Object>> delete(@RequestParam Long id) {
    return ResultModel.ok(tags.delete(id));
  }

  /** 认可 / 取消认可个性标签。 */
  @PostMapping("/recognize")
  public ResultModel<Map<String, Object>> recognize(@RequestParam Long id) {
    return ResultModel.ok(tags.recognize(id));
  }
}
