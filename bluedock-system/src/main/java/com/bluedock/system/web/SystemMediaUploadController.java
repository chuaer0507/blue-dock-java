package com.bluedock.system.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.service.SystemMediaUploadService;
import com.bluedock.system.service.UploadObjectService;
import com.bluedock.system.upload.UploadObjectView;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/system")
public class SystemMediaUploadController {
  private final SystemMediaUploadService media;
  private final UploadObjectService uploads;

  public SystemMediaUploadController(SystemMediaUploadService media, UploadObjectService uploads) {
    this.media = media;
    this.uploads = uploads;
  }

  @PostMapping("/imageUpload")
  public ResultModel<Map<String, Object>> imageUpload(@RequestParam("file") MultipartFile file) {
    return ResultModel.ok(media.imageUpload(file));
  }

  /** 本人图片空间：读 {@code bluedock_upload_objects}（media），wire {@code {dirs,files}}。 */
  @GetMapping("/imageView")
  public ResultModel<Map<String, Object>> imageView(
      @RequestParam(required = false) String path) {
    return ResultModel.ok(uploads.imageView(path));
  }

  @PostMapping("/fileUpload")
  public ResultModel<Map<String, Object>> fileUpload(@RequestParam("file") MultipartFile file) {
    return ResultModel.ok(media.fileUpload(file));
  }

  @GetMapping("/uploads")
  public ResultModel<Map<String, Object>> listUploads(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    Integer size = pageSize;
    return ResultModel.ok(uploads.list(category, q, page, size));
  }

  @PostMapping("/uploads")
  public ResultModel<UploadObjectView> postUpload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String category) {
    return ResultModel.ok(uploads.adminUpload(file, category));
  }

  @DeleteMapping("/uploads")
  public ResultModel<Map<String, Object>> deleteUpload(@RequestParam long id) {
    return ResultModel.ok(uploads.delete(id));
  }
}
