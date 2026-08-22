package com.bluedock.user.app.sort.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.app.sort.service.UserAppSortService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAppSortController {
  private final UserAppSortService appSort;

  public UserAppSortController(UserAppSortService appSort) {
    this.appSort = appSort;
  }

  @GetMapping("/appSort")
  public ResultModel<Map<String, Object>> get() {
    return ResultModel.ok(appSort.get());
  }

  @PostMapping("/appSort/save")
  public ResultModel<Map<String, Object>> save(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(appSort.save(b.get("sorts")));
  }
}
