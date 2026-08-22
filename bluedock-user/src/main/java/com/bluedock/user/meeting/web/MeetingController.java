package com.bluedock.user.meeting.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.meeting.service.MeetingService;
import com.bluedock.user.meeting.web.dto.MeetingOpenView;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/meeting")
public class MeetingController {
  private final MeetingService meetings;

  public MeetingController(MeetingService meetings) {
    this.meetings = meetings;
  }

  @GetMapping("/open")
  public ResultModel<MeetingOpenView> open(
      @RequestParam String type,
      @RequestParam(required = false) String meetingId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String userIds,
      @RequestParam(required = false) String shareKey,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String userImage) {
    return ResultModel.ok(
        meetings.open(type, meetingId, name, userIds, shareKey, username, userImage));
  }

  @GetMapping("/link")
  public ResultModel<Map<String, Object>> link(
      @RequestParam String meetingId, @RequestParam(required = false) String shareKey) {
    return ResultModel.ok(meetings.link(meetingId, shareKey));
  }

  @GetMapping("/tourist")
  public ResultModel<Map<String, Object>> tourist(@RequestParam String touristId) {
    return ResultModel.ok(meetings.tourist(touristId));
  }

  @GetMapping("/invitation")
  public ResultModel<Map<String, Object>> invitation(
      @RequestParam String meetingId, @RequestParam(required = false) String userIds) {
    return ResultModel.ok(meetings.invitation(meetingId, userIds));
  }
}
