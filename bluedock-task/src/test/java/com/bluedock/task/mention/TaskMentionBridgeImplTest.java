package com.bluedock.task.mention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.service.TaskRelationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskMentionBridgeImplTest {
  @Mock TaskRepository tasks;
  @Mock TaskRelationService relations;
  @InjectMocks TaskMentionBridgeImpl bridge;

  @Test
  void parseHtmlAndLegacy() {
    Set<Long> ids =
        TaskMentionBridgeImpl.parseMentionIds(
            "see <span class=\"mention task\" data-id=\"11\">#A</span> and [:#:22:B:]");
    assertEquals(Set.of(11L, 22L), ids);
  }

  @Test
  void extractTextFromJson() {
    assertEquals(
        "<span class=\"mention task\" data-id=\"3\">#x</span>",
        TaskMentionBridgeImpl.extractText(
            "{\"text\":\"<span class=\\\"mention task\\\" data-id=\\\"3\\\">#x</span>\"}"));
  }

  @Test
  void recordMentions_linksPairs() {
    when(tasks.listIdsByDialogId(9L)).thenReturn(List.of(100L));
    String body = "<span class=\"mention task\" data-id=\"200\">#T</span>";

    bridge.recordMentionsFromMessage(9L, 55L, 1L, body);

    verify(relations).link(100L, 200L, 9L, 55L, 1L);
  }

  @Test
  void recordMentions_skipsWhenNoSourceTask() {
    when(tasks.listIdsByDialogId(9L)).thenReturn(List.of());
    bridge.recordMentionsFromMessage(
        9L, 55L, 1L, "<span class=\"mention task\" data-id=\"200\">#T</span>");
    verify(relations, never()).link(anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
  }

  @Test
  void recordMentions_swallowsBusinessException() {
    when(tasks.listIdsByDialogId(9L)).thenReturn(List.of(100L));
    when(relations.link(100L, 200L, 9L, 55L, 1L))
        .thenThrow(new BusinessException(ErrorCodes.PROJECT_DENIED, "x"));

    bridge.recordMentionsFromMessage(
        9L, 55L, 1L, "<span class=\"mention task\" data-id=\"200\">#T</span>");
    verify(relations).link(100L, 200L, 9L, 55L, 1L);
  }

  @Test
  void parse_empty() {
    assertTrue(TaskMentionBridgeImpl.parseMentionIds("plain").isEmpty());
  }
}
