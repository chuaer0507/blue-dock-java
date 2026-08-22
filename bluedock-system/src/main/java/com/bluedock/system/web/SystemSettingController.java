package com.bluedock.system.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.oss.OssSettingsDocument;
import com.bluedock.system.service.AiBotSettingService;
import com.bluedock.system.service.AppPushSettingService;
import com.bluedock.system.service.AttendanceSettingService;
import com.bluedock.system.service.ColumnTemplateSettingService;
import com.bluedock.system.service.EmailSettingService;
import com.bluedock.system.service.FileSettingService;
import com.bluedock.system.service.LdapSettingService;
import com.bluedock.system.service.MeetingSettingService;
import com.bluedock.system.service.OssSettingService;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.system.service.TaskPrioritySettingService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemSettingController {
  private final LdapSettingService ldap;
  private final EmailSettingService email;
  private final AppPushSettingService appPush;
  private final MeetingSettingService meeting;
  private final AttendanceSettingService attendance;
  private final SystemGeneralSettingService general;
  private final FileSettingService file;
  private final OssSettingService oss;
  private final AiBotSettingService aiBot;
  private final TaskPrioritySettingService priority;
  private final ColumnTemplateSettingService columnTemplate;

  public SystemSettingController(
      LdapSettingService ldap,
      EmailSettingService email,
      AppPushSettingService appPush,
      MeetingSettingService meeting,
      AttendanceSettingService attendance,
      SystemGeneralSettingService general,
      FileSettingService file,
      OssSettingService oss,
      AiBotSettingService aiBot,
      TaskPrioritySettingService priority,
      ColumnTemplateSettingService columnTemplate) {
    this.ldap = ldap;
    this.email = email;
    this.appPush = appPush;
    this.meeting = meeting;
    this.attendance = attendance;
    this.general = general;
    this.file = file;
    this.oss = oss;
    this.aiBot = aiBot;
    this.priority = priority;
    this.columnTemplate = columnTemplate;
  }

  @GetMapping("/setting")
  public ResultModel<Map<String, Object>> generalGet() {
    return ResultModel.ok(general.get());
  }

  @PostMapping("/setting")
  public ResultModel<Map<String, Object>> generalSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(general.save(body == null ? Map.of() : body));
  }

  /** 第三方 / LDAP 设置（契约：thirdAccess）。 */
  @GetMapping("/setting/thirdAccess")
  public ResultModel<Map<String, Object>> thirdAccessGet() {
    return ResultModel.ok(ldap.get());
  }

  @PostMapping("/setting/thirdAccess")
  public ResultModel<Map<String, Object>> thirdAccessSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(ldap.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/thirdAccess/testLdap")
  public ResultModel<Map<String, Object>> testLdap(@RequestParam(required = false) String ignored) {
    return ResultModel.ok(ldap.test());
  }

  @GetMapping("/setting/email")
  public ResultModel<Map<String, Object>> emailGet() {
    return ResultModel.ok(email.get());
  }

  @PostMapping("/setting/email")
  public ResultModel<Map<String, Object>> emailSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(email.save(body == null ? Map.of() : body));
  }

  @GetMapping("/email/check")
  public ResultModel<Map<String, Object>> emailCheck(@RequestParam String email) {
    return ResultModel.ok(this.email.check(email));
  }

  @GetMapping("/setting/appPush")
  public ResultModel<Map<String, Object>> appPushGet() {
    return ResultModel.ok(appPush.get());
  }

  @PostMapping("/setting/appPush")
  public ResultModel<Map<String, Object>> appPushSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(appPush.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/meeting")
  public ResultModel<Map<String, Object>> meetingGet() {
    return ResultModel.ok(meeting.get());
  }

  @PostMapping("/setting/meeting")
  public ResultModel<Map<String, Object>> meetingSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(meeting.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/attendance")
  public ResultModel<Map<String, Object>> attendanceGet() {
    return ResultModel.ok(attendance.get());
  }

  @PostMapping("/setting/attendance")
  public ResultModel<Map<String, Object>> attendanceSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(attendance.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/file")
  public ResultModel<Map<String, Object>> fileGet() {
    return ResultModel.ok(file.get());
  }

  @PostMapping("/setting/file")
  public ResultModel<Map<String, Object>> fileSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(file.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/oss")
  public ResultModel<OssSettingsDocument> ossGet() {
    return ResultModel.ok(oss.get());
  }

  @PostMapping("/setting/oss")
  public ResultModel<OssSettingsDocument> ossSave(
      @RequestBody(required = false) OssSettingsDocument body) {
    return ResultModel.ok(oss.save(body));
  }

  /** 对象存储连通性检测（put 探针 + delete）。 */
  @GetMapping("/oss/check")
  public ResultModel<Map<String, Object>> ossCheck() {
    return ResultModel.ok(oss.check());
  }

  @GetMapping("/setting/aiBot")
  public ResultModel<Map<String, Object>> aibotGet() {
    return ResultModel.ok(aiBot.get());
  }

  @PostMapping("/setting/aiBot")
  public ResultModel<Map<String, Object>> aibotSave(
      @RequestBody(required = false) Map<String, Object> body) {
    return ResultModel.ok(aiBot.save(body == null ? Map.of() : body));
  }

  @GetMapping("/setting/aiBotModels")
  public ResultModel<List<Map<String, Object>>> aibotModels() {
    return ResultModel.ok(aiBot.models());
  }

  @GetMapping("/setting/aiBotDefaultModels")
  public ResultModel<List<Map<String, Object>>> aibotDefModels() {
    return ResultModel.ok(aiBot.defModels());
  }

  @PostMapping("/priority")
  public ResultModel<List<Map<String, Object>>> priority(
      @RequestParam(required = false) String type,
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    String t = type != null ? type : str(b.get("type"));
    if ("save".equalsIgnoreCase(t == null ? "" : t.trim())) {
      return ResultModel.ok(priority.save(b.get("list")));
    }
    return ResultModel.ok(priority.get());
  }

  @PostMapping("/column/template")
  public ResultModel<List<Map<String, Object>>> columnTemplate(
      @RequestParam(required = false) String type,
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    String t = type != null ? type : str(b.get("type"));
    if ("save".equalsIgnoreCase(t == null ? "" : t.trim())) {
      return ResultModel.ok(columnTemplate.save(b.get("list")));
    }
    return ResultModel.ok(columnTemplate.get());
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
