package com.bluedock.org.department.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.org.department.service.DepartmentService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class DepartmentController {
  private final DepartmentService departments;

  public DepartmentController(DepartmentService departments) {
    this.departments = departments;
  }

  @GetMapping("/department/list")
  public ResultModel<List<Map<String, Object>>> list() {
    return ResultModel.ok(departments.list());
  }

  @GetMapping("/department/add")
  public ResultModel<Map<String, Object>> add(
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam(required = false) Long parentId,
      @RequestParam(required = false) Long ownerUserId) {
    return ResultModel.ok(departments.add(id, name, parentId, ownerUserId));
  }

  @PostMapping("/department/addDeputy")
  public ResultModel<Void> addDeputy(@RequestParam Long id, @RequestParam Long userId) {
    departments.addDeputy(id, userId);
    return ResultModel.ok();
  }

  @PostMapping("/department/deleteDeputy")
  public ResultModel<Void> delDeputy(@RequestParam Long id, @RequestParam Long userId) {
    departments.delDeputy(id, userId);
    return ResultModel.ok();
  }

  @GetMapping("/department/delete")
  public ResultModel<Void> del(@RequestParam Long id) {
    departments.delete(id);
    return ResultModel.ok();
  }

  @GetMapping("/department/sync")
  public ResultModel<Map<String, Object>> sync(@RequestParam Long id) {
    return ResultModel.ok(departments.sync(id));
  }

  @GetMapping("/info/departments")
  public ResultModel<List<Map<String, Object>>> myDepartments() {
    return ResultModel.ok(departments.myDepartments());
  }

  @GetMapping("/info/managedDepartments")
  public ResultModel<List<Map<String, Object>>> managedDepartments() {
    return ResultModel.ok(departments.managedDepartments());
  }
}
