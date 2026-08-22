package com.bluedock.file.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.file.service.UploadService;
import com.bluedock.file.web.dto.UploadChunkView;
import com.bluedock.file.web.dto.UploadInitView;
import com.bluedock.file.web.dto.UploadMergeView;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
  private final UploadService uploads;

  public UploadController(UploadService uploads) {
    this.uploads = uploads;
  }

  @PostMapping("/init")
  public ResultModel<UploadInitView> init(
      @RequestParam String hash,
      @RequestParam long size,
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "file_cabinet") String scene,
      @RequestParam(required = false) Long parentId,
      @RequestParam(required = false) Long taskId) {
    return ResultModel.ok(uploads.init(hash, size, name, scene, parentId, taskId));
  }

  @PostMapping("/chunk")
  public ResultModel<UploadChunkView> chunk(
      @RequestParam String uploadId,
      @RequestParam int index,
      @RequestParam("blob") MultipartFile blob) {
    return ResultModel.ok(uploads.chunk(uploadId, index, blob));
  }

  @PostMapping("/merge")
  public ResultModel<UploadMergeView> merge(@RequestParam String uploadId) {
    return ResultModel.ok(uploads.merge(uploadId));
  }

  @PostMapping("/cancel")
  public ResultModel<Void> cancel(@RequestParam String uploadId) {
    uploads.cancel(uploadId);
    return ResultModel.ok();
  }
}
