package com.bluedock.file.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.file.service.FileContentService;
import com.bluedock.file.service.FileOfficeService;
import com.bluedock.file.service.FilePackService;
import com.bluedock.file.service.FileService;
import com.bluedock.file.service.FileShareService;
import com.bluedock.file.web.dto.FileContentHistoryItem;
import com.bluedock.file.web.dto.FileContentView;
import com.bluedock.file.web.dto.FileLinkView;
import com.bluedock.file.web.dto.FilePackView;
import com.bluedock.file.web.dto.FileShareView;
import com.bluedock.file.web.dto.FileView;
import com.bluedock.file.web.dto.OfficeTokenView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/file")
public class FileController {
  private final FileService files;
  private final FileContentService contents;
  private final FileShareService shares;
  private final FilePackService packs;
  private final FileOfficeService office;

  public FileController(
      FileService files,
      FileContentService contents,
      FileShareService shares,
      FilePackService packs,
      FileOfficeService office) {
    this.files = files;
    this.contents = contents;
    this.shares = shares;
    this.packs = packs;
    this.office = office;
  }

  @GetMapping("/lists")
  public ResultModel<List<FileView>> lists(@RequestParam(required = false) Long parentId) {
    return ResultModel.ok(files.lists(parentId));
  }

  @GetMapping("/one")
  public ResultModel<FileView> one(@RequestParam long id) {
    return ResultModel.ok(files.one(id));
  }

  @GetMapping("/fetch")
  public ResultModel<String> fetch(
      @RequestParam(required = false) Long id, @RequestParam(required = false) String path) {
    return ResultModel.ok(files.fetch(id, path));
  }

  @GetMapping("/search")
  public ResultModel<List<FileView>> search(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(files.search(key, take));
  }

  @GetMapping("/add")
  public ResultModel<FileView> add(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long parentId,
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "folder") String type) {
    return ResultModel.ok(files.add(id, parentId, name, type));
  }

  @GetMapping("/copy")
  public ResultModel<FileView> copy(
      @RequestParam long id, @RequestParam(required = false) Long parentId) {
    return ResultModel.ok(files.copy(id, parentId));
  }

  @GetMapping("/move")
  public ResultModel<FileView> move(@RequestParam long id, @RequestParam long parentId) {
    return ResultModel.ok(files.move(id, parentId));
  }

  @GetMapping("/remove")
  public ResultModel<Void> remove(@RequestParam long id) {
    files.remove(id);
    return ResultModel.ok();
  }

  @GetMapping("/trash")
  public ResultModel<List<FileView>> trash() {
    return ResultModel.ok(files.trash());
  }

  @GetMapping("/restore")
  public ResultModel<FileView> restore(@RequestParam long id) {
    return ResultModel.ok(files.restore(id));
  }

  /**
   * 鉴权流式读取文件二进制（图片预览等）；非信封，直接返回 body。
   */
  @GetMapping("/raw")
  public ResponseEntity<Resource> raw(@RequestParam long id) throws java.io.IOException {
    FileService.RawFile raw = files.raw(id);
    String probed = Files.probeContentType(raw.path());
    MediaType media =
        probed == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(probed);
    String filename = raw.name() == null ? "file" : raw.name().replace("\"", "");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .contentType(media)
        .contentLength(Files.size(raw.path()))
        .body(new FileSystemResource(raw.path()));
  }

  @GetMapping("/content")
  public ResultModel<FileContentView> content(@RequestParam long id) {
    return ResultModel.ok(contents.content(id));
  }

  @GetMapping("/content/save")
  public ResultModel<FileContentView> contentSave(
      @RequestParam long id, @RequestParam(required = false, defaultValue = "") String content) {
    return ResultModel.ok(contents.save(id, content));
  }

  @GetMapping("/content/history")
  public ResultModel<List<FileContentHistoryItem>> contentHistory(
      @RequestParam long id, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(contents.history(id, take));
  }

  @GetMapping("/content/restore")
  public ResultModel<FileContentView> contentRestore(
      @RequestParam long id, @RequestParam long contentId) {
    return ResultModel.ok(contents.restore(id, contentId));
  }

  @GetMapping("/content/upload")
  public ResultModel<FileContentView> contentUpload(
      @RequestParam long id, @RequestParam String uploadId) {
    return ResultModel.ok(contents.uploadFromSession(id, uploadId));
  }

  @GetMapping("/office/token")
  public ResultModel<OfficeTokenView> officeToken(
      @RequestParam long id, @RequestParam(required = false, defaultValue = "edit") String mode) {
    return ResultModel.ok(office.token(id, mode));
  }

  /**
   * OnlyOffice：{@code action=download&amp;token=} 拉文档；{@code token}+{@code status}/{@code url} 回调回写；
   * 或登录态 {@code id}+{@code url} 主动回写。
   */
  @GetMapping("/content/office")
  public Object contentOfficeGet(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String token,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String url,
      @RequestParam(required = false) Long id) {
    return contentOffice(action, token, status, url, id);
  }

  @PostMapping("/content/office")
  public Object contentOfficePost(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String token,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String url,
      @RequestParam(required = false) Long id,
      @RequestBody(required = false) Map<String, Object> body) {
    if (body != null) {
      if (status == null && body.get("status") instanceof Number n) {
        status = n.intValue();
      }
      if ((url == null || url.isBlank()) && body.get("url") != null) {
        url = String.valueOf(body.get("url"));
      }
    }
    return contentOffice(action, token, status, url, id);
  }

  private Object contentOffice(
      String action, String token, Integer status, String url, Long id) {
    if ("download".equals(action) && token != null && !token.isBlank()) {
      Path file = office.resolveDownload(token);
      String name = office.downloadFilename(token);
      Resource body = new FileSystemResource(file);
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + name.replace("\"", "") + "\"")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(body);
    }
    if (token != null && !token.isBlank()) {
      office.saveFromOffice(token, status, url);
      return Map.of("error", 0);
    }
    if (id != null && id > 0) {
      return ResultModel.ok(office.saveFromUrl(id, url));
    }
    return Map.of("error", 1);
  }

  @GetMapping("/share")
  public ResultModel<FileShareView> share(@RequestParam long id) {
    return ResultModel.ok(shares.share(id));
  }

  @GetMapping("/share/update")
  public ResultModel<FileShareView> shareUpdate(
      @RequestParam long id,
      @RequestParam(required = false) String userIds,
      @RequestParam(required = false) String removeUserIds,
      @RequestParam(required = false) Integer permission) {
    return ResultModel.ok(shares.update(id, userIds, removeUserIds, permission));
  }

  @GetMapping("/share/out")
  public ResultModel<Void> shareOut(@RequestParam long id) {
    shares.shareOut(id);
    return ResultModel.ok();
  }

  @GetMapping("/link")
  public ResultModel<FileLinkView> link(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) Boolean refresh,
      @RequestParam(required = false) Integer permission,
      @RequestParam(required = false) Integer allowGuest) {
    if (code != null && !code.isBlank()) {
      return ResultModel.ok(shares.linkByCode(code));
    }
    if (id == null) {
      return ResultModel.ok(shares.linkByCode(""));
    }
    return ResultModel.ok(shares.link(id, refresh, permission, allowGuest));
  }

  /**
   * 打包下载：{@code ids} 创建 zip；{@code packId} 查询元数据；{@code packId}+{@code download=1} 流式下载。
   */
  @GetMapping("/download/pack")
  public Object downloadPack(
      @RequestParam(required = false) String ids,
      @RequestParam(required = false) String packId,
      @RequestParam(required = false) Boolean download) {
    if (packId != null && !packId.isBlank() && Boolean.TRUE.equals(download)) {
      Path zip = packs.resolvePackFile(packId);
      FilePackView meta = packs.status(packId);
      Resource body = new FileSystemResource(zip);
      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + meta.name().replace("\"", "") + "\"")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .contentLength(meta.size())
          .body(body);
    }
    if (packId != null && !packId.isBlank()) {
      return ResultModel.ok(packs.status(packId));
    }
    return ResultModel.ok(packs.pack(ids));
  }
}
