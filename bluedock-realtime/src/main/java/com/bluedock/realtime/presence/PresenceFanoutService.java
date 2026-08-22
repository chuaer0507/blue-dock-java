package com.bluedock.realtime.presence;

import com.bluedock.common.realtime.PresencePeerLookup;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.util.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** WS 首连 / 末断 → 向会话对端扇出 presence 帧。 */
@Component
public class PresenceFanoutService {
  private static final Logger log = LoggerFactory.getLogger(PresenceFanoutService.class);

  private final RealtimeFanoutPublisher fanout;
  private final ObjectProvider<PresencePeerLookup> peers;

  public PresenceFanoutService(
      RealtimeFanoutPublisher fanout, ObjectProvider<PresencePeerLookup> peers) {
    this.fanout = fanout;
    this.peers = peers;
  }

  public void publishOnline(long userId) {
    publish(userId, RealtimeEventTypes.PRESENCE_ONLINE, true);
  }

  public void publishOffline(long userId) {
    publish(userId, RealtimeEventTypes.PRESENCE_OFFLINE, false);
  }

  private void publish(long userId, String type, boolean online) {
    if (userId <= 0) {
      return;
    }
    PresencePeerLookup lookup = peers.getIfAvailable();
    if (lookup == null) {
      log.debug("presence skip: no PresencePeerLookup");
      return;
    }
    List<Long> peerIds = lookup.peerUserIds(userId);
    if (peerIds == null || peerIds.isEmpty()) {
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", userId);
    data.put("online", online);
    fanout.publish(
        new RealtimeFanoutEvent(IdGenerator.nextId() + "", type, List.copyOf(peerIds), data));
  }
}
