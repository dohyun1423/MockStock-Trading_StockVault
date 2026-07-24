// Verifies realtime data filtering and cached snapshot delivery to browser sessions.
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockRealtimeBroadcasterTest {

    @Mock
    private StockRealtimeSessionRegistry sessionRegistry;

    @Mock
    private WebSocketSession webSocketSession;

    private ObjectMapper objectMapper;
    private StockRealtimeBroadcaster broadcaster;

    // Creates a broadcaster with a real in-memory snapshot cache for each test.
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        broadcaster = new StockRealtimeBroadcaster(
                objectMapper,
                sessionRegistry,
                new StockRealtimeSnapshotCache()
        );
    }

    // Verifies that an all-zero orderbook is not sent to browsers.
    @Test
    void rejectAllZeroOrderbook() {
        KisRealtimeOrderbookMessage zeroOrderbook = createOrderbook(0L, 0L, 0L, 0L);

        assertThat(broadcaster.broadcastOrderbook(zeroOrderbook)).isFalse();
        verify(sessionRegistry, never()).broadcast(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    // Verifies that a new browser receives the last valid trade as a snapshot message.
    @Test
    void sendLatestTradeAsSnapshot() throws Exception {
        KisRealtimeTradeMessage tradeMessage = KisRealtimeTradeMessage.builder()
                .symbol("005930")
                .tradeTime("153000")
                .currentPrice(83_500L)
                .marketSession(MarketSession.REGULAR)
                .build();

        assertThat(broadcaster.broadcastTrade(tradeMessage)).isTrue();
        when(webSocketSession.isOpen()).thenReturn(true);

        broadcaster.sendLatestSnapshots("005930", webSocketSession);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());

        JsonNode payload = objectMapper.readTree(messageCaptor.getValue().getPayload());

        assertThat(payload.path("type").asText()).isEqualTo("TRADE");
        assertThat(payload.path("symbol").asText()).isEqualTo("005930");
        assertThat(payload.path("snapshot").asBoolean()).isTrue();
        assertThat(payload.path("data").path("currentPrice").asLong()).isEqualTo(83_500L);
    }

    // Creates a one-level orderbook for broadcaster validation tests.
    private KisRealtimeOrderbookMessage createOrderbook(
            long askPrice,
            long askQuantity,
            long bidPrice,
            long bidQuantity
    ) {
        return KisRealtimeOrderbookMessage.builder()
                .symbol("005930")
                .businessTime("153000")
                .levels(List.of(KisRealtimeOrderbookLevel.builder()
                        .level(1)
                        .askPrice(askPrice)
                        .askQuantity(askQuantity)
                        .bidPrice(bidPrice)
                        .bidQuantity(bidQuantity)
                        .build()))
                .marketSession(MarketSession.REGULAR)
                .build();
    }
}
