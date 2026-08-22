package com.bluedock.messenger.sticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StickerSearchServiceTest {
  private final StickerSearchService service = new StickerSearchService(new ObjectMapper());

  @Test
  void search_blankReturnsEmpty() {
    assertTrue(service.search("").isEmpty());
    assertTrue(service.search(null).isEmpty());
  }

  @Test
  void parseItems_ok() throws Exception {
    String raw =
        """
        {"status":0,"data":{"picResult":{"items":[
          {"title":"猫","thumbUrl":"https://x/a.gif","thumbHeight":48,"thumbWidth":48},
          {"title":"小","thumbUrl":"https://x/b.gif","thumbHeight":5,"thumbWidth":5}
        ]}}}
        """;
    List<Map<String, Object>> list = service.parseItems(raw);
    assertEquals(1, list.size());
    assertEquals("猫", list.get(0).get("name"));
    assertEquals("https://x/a.gif", list.get(0).get("src"));
  }

  @Test
  void validatePublicHttpUrl_rejectsPrivate() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StickerSearchService.validatePublicHttpUrl("http://127.0.0.1/a.gif"));
    assertThrows(
        IllegalArgumentException.class,
        () -> StickerSearchService.validatePublicHttpUrl("ftp://example.com/a.gif"));
    assertNull(StickerSearchService.extensionFromContentType("text/html"));
    assertEquals("gif", StickerSearchService.extensionFromPath("/x/a.gif"));
  }
}
