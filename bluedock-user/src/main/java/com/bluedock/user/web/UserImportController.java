package com.bluedock.user.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.service.UserImportService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/import")
public class UserImportController {
  private final UserImportService imports;

  public UserImportController(UserImportService imports) {
    this.imports = imports;
  }

  @GetMapping("/template")
  public ResponseEntity<byte[]> template() {
    byte[] body = imports.templateCsv();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"user-import-template.csv\"")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(body);
  }

  @PostMapping("/preview")
  public ResultModel<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) {
    return ResultModel.ok(imports.preview(file));
  }

  @PostMapping
  public ResultModel<Map<String, Object>> importUsers(@RequestBody Map<String, Object> body) {
    Object rowsObj = body == null ? null : body.get("rows");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows =
        rowsObj instanceof List<?> list
            ? list.stream()
                .filter(Map.class::isInstance)
                .map(m -> (Map<String, Object>) m)
                .toList()
            : List.of();
    String keyId = body == null || body.get("keyId") == null ? null : String.valueOf(body.get("keyId"));
    return ResultModel.ok(imports.importUsers(rows, keyId));
  }
}
