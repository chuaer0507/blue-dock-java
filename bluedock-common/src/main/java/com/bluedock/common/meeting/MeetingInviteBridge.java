package com.bluedock.common.meeting;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 会议邀请卡片 / 结束态同步到 messenger；由 messenger 实现。 */
public interface MeetingInviteBridge {
  String MEETING_ALERT_EMAIL = "meeting-alert@bot.system";

  /** 向邀请人发送 type=meeting 对话卡片；返回已发送消息摘要。 */
  List<Map<String, Object>> sendInvites(
      Map<String, Object> meetingPayload, long inviterUserId, Collection<Long> inviteeUserIds);

  /** 会议结束后合并卡片 payload（含 endAt）并 WS 推送 update。 */
  void markMeetingEnded(String meetingId, Map<String, Object> meetingPayload);
}
