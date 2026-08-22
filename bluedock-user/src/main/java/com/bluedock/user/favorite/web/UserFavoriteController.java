package com.bluedock.user.favorite.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.favorite.service.UserFavoriteService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserFavoriteController {
  private final UserFavoriteService favorites;

  public UserFavoriteController(UserFavoriteService favorites) {
    this.favorites = favorites;
  }

  @GetMapping("/favorites")
  public ResultModel<Map<String, Object>> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(
        favorites.list(type, page, pageSize));
  }

  @PostMapping("/favorite/toggle")
  public ResultModel<Map<String, Object>> toggle(
      @RequestParam String type, @RequestParam Long id) {
    return ResultModel.ok(favorites.toggle(type, id));
  }

  @PostMapping("/favorite/remark")
  public ResultModel<Map<String, Object>> remark(
      @RequestParam String type, @RequestParam Long id, @RequestParam String remark) {
    return ResultModel.ok(favorites.remark(type, id, remark));
  }

  @PostMapping("/favorites/clean")
  public ResultModel<Map<String, Object>> clean(@RequestParam(required = false) String type) {
    return ResultModel.ok(favorites.clean(type));
  }

  @GetMapping("/favorite/check")
  public ResultModel<Map<String, Object>> check(@RequestParam String type, @RequestParam Long id) {
    return ResultModel.ok(favorites.check(type, id));
  }
}
