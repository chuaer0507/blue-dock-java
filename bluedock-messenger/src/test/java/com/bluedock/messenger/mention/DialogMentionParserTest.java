package com.bluedock.messenger.mention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DialogMentionParserTest {
  @Test
  void parse_userHtmlAndLegacy() {
    DialogMentionParser.Result r =
        DialogMentionParser.parse(
            "<span class=\"mention user\" data-id=\"9\">@a</span> and [:@:11:b:]");
    assertFalse(r.all());
    assertEquals(2, r.userIds().size());
    assertTrue(r.userIds().contains(9L));
    assertTrue(r.userIds().contains(11L));
  }

  @Test
  void parse_all() {
    DialogMentionParser.Result r =
        DialogMentionParser.parse("<span class=\"mention all\">@所有人</span>");
    assertTrue(r.all());
  }

  @Test
  void parse_ignoresTaskMention() {
    DialogMentionParser.Result r =
        DialogMentionParser.parse("<span class=\"mention task\" data-id=\"77\">#T</span>");
    assertTrue(r.userIds().isEmpty());
  }
}
