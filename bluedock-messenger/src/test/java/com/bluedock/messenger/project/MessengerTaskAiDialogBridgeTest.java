package com.bluedock.messenger.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessengerTaskAiDialogBridgeTest {
  @Test
  void patchStatus_updatesMatchingType() {
    String body =
        """
        ### 描述建议
        :::ai-action{type=description task_id=50 message_id=9}:::
        hello
        :::

        ### 子任务
        :::ai-action{type=subtasks task_id=50 message_id=9}:::
        - a
        :::
        """;
    String out =
        MessengerTaskAiDialogBridge.patchStatus(body, "description", "applied", 0, 0);
    assertTrue(out.contains("type=description"));
    assertTrue(out.contains("status=applied"));
    assertTrue(out.contains(":::ai-action{type=subtasks task_id=50 message_id=9}:::"));
  }

  @Test
  void patchStatus_filtersByUserId() {
    String body =
        """
        :::ai-action{type=assignee task_id=1 message_id=2 userId=10}:::
        a
        :::
        :::ai-action{type=assignee task_id=1 message_id=2 userId=20}:::
        b
        :::
        """;
    String out =
        MessengerTaskAiDialogBridge.patchStatus(body, "assignee", "dismissed", 20, 0);
    assertTrue(out.contains("userId=10}:::"));
    assertTrue(out.contains("userId=20 status=dismissed}:::") || out.contains("status=dismissed"));
    assertEquals(1, out.split("status=dismissed", -1).length - 1);
  }
}
