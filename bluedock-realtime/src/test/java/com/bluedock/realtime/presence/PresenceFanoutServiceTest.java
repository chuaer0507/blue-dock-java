package com.bluedock.realtime.presence;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.realtime.PresencePeerLookup;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class PresenceFanoutServiceTest {
  @Mock RealtimeFanoutPublisher fanout;
  @Mock ObjectProvider<PresencePeerLookup> peers;
  @Mock PresencePeerLookup lookup;

  @Test
  void publishOnline_fansOutToPeers() {
    when(peers.getIfAvailable()).thenReturn(lookup);
    when(lookup.peerUserIds(7L)).thenReturn(List.of(2L, 3L));
    PresenceFanoutService service = new PresenceFanoutService(fanout, peers);

    service.publishOnline(7L);

    verify(fanout)
        .publish(
            argThat(
                e ->
                    RealtimeEventTypes.PRESENCE_ONLINE.equals(e.type())
                        && e.userIds().equals(List.of(2L, 3L))
                        && Long.valueOf(7L).equals(e.data().get("userId"))
                        && Boolean.TRUE.equals(e.data().get("online"))));
  }

  @Test
  void publishOffline_skipsWhenNoPeers() {
    when(peers.getIfAvailable()).thenReturn(lookup);
    when(lookup.peerUserIds(7L)).thenReturn(List.of());
    PresenceFanoutService service = new PresenceFanoutService(fanout, peers);

    service.publishOffline(7L);

    verify(fanout, never()).publish(org.mockito.ArgumentMatchers.any());
  }
}
