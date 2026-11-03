package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.VolumeRankItem;
import com.stock.mockstock.domain.stock.dto.VolumeRankingResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.provider.KisVolumeRankingProvider;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class VolumeRankingServiceTest {
    private final KisVolumeRankingProvider provider = mock(KisVolumeRankingProvider.class);
    private final StockRepository repository = mock(StockRepository.class);
    private final MutableClock clock = new MutableClock();
    private VolumeRankingService service;

    @BeforeEach
    void setUp() {
        service = new VolumeRankingService(provider, repository, clock);
    }

    @Test
    void sortsDeduplicatesAndLimitsToThirtyWithoutHidingUnregisteredStocks() {
        List<VolumeRankItem> input = new ArrayList<>(IntStream.rangeClosed(1, 35)
                .mapToObj(i -> item(String.format("%06d", i), i * 100L)).toList());
        input.add(item("000035", 3500));
        when(provider.getVolumeRanking()).thenReturn(input);
        when(repository.findBySymbolIn(anyList())).thenReturn(List.of(
                Stock.builder().symbol("000035").name("DB에 등록된 이름").build()));

        VolumeRankingResponse result = service.getRanking();

        assertThat(result.items()).hasSize(30);
        assertThat(result.items()).extracting(VolumeRankItem::symbol).doesNotHaveDuplicates();
        assertThat(result.items()).extracting(VolumeRankItem::rank).containsExactlyElementsOf(
                IntStream.rangeClosed(1, 30).boxed().toList());
        assertThat(result.items().getFirst().symbol()).isEqualTo("000035");
        assertThat(result.items().getFirst().name()).isEqualTo("DB에 등록된 이름");
        assertThat(result.items().getFirst().available()).isTrue();
        assertThat(result.items().getLast().symbol()).isEqualTo("000006");
        assertThat(result.items().getLast().available()).isFalse();
        verify(repository).findBySymbolIn(argThat(symbols -> symbols.size() == 30));
    }

    @Test
    void preservesEtnCodeAndRankEvenWhenItIsMissingFromTheStockMaster() {
        when(provider.getVolumeRanking()).thenReturn(List.of(item("Q530107", 200), item("005930", 100)));
        when(repository.findBySymbolIn(anyList())).thenReturn(List.of(
                Stock.builder().symbol("005930").name("삼성전자").build()));

        var items = service.getRanking().items();
        assertThat(items).extracting(VolumeRankItem::symbol).containsExactly("Q530107", "005930");
        assertThat(items.getFirst().rank()).isEqualTo(1);
        assertThat(items.getFirst().available()).isFalse();
        assertThat(items.getLast().available()).isTrue();
    }

    @Test
    void sharesOneFetchAcrossConcurrentRequestsAndRefreshesAfterSixtySeconds() throws Exception {
        when(provider.getVolumeRanking()).thenReturn(List.of(item("005930", 100)));
        when(repository.findBySymbolIn(anyList())).thenReturn(List.of());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<VolumeRankingResponse>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> { start.await(); return service.getRanking(); }));
            }
            start.countDown();
            VolumeRankingResponse first = results.getFirst().get();
            for (var result : results) assertThat(result.get()).isSameAs(first);
        }
        verify(provider, times(1)).getVolumeRanking();
        clock.advance(59);
        service.getRanking();
        verify(provider, times(1)).getVolumeRanking();
        clock.advance(1);
        service.getRanking();
        verify(provider, times(2)).getVolumeRanking();
    }

    @Test
    void retainsLastSnapshotOnFailureAndRecoversOnNextInterval() {
        when(provider.getVolumeRanking()).thenReturn(List.of(item("005930", 100)))
                .thenThrow(new IllegalStateException("upstream failure"))
                .thenReturn(List.of(item("005930", 200)));
        when(repository.findBySymbolIn(anyList())).thenReturn(List.of());
        VolumeRankingResponse first = service.getRanking();
        clock.advance(60);
        VolumeRankingResponse stale = service.getRanking();
        assertThat(stale.stale()).isTrue();
        assertThat(stale.items()).isEqualTo(first.items());
        assertThat(stale.fetchedAt()).isEqualTo(first.fetchedAt());
        service.getRanking();
        verify(provider, times(2)).getVolumeRanking();
        clock.advance(60);
        VolumeRankingResponse recovered = service.getRanking();
        assertThat(recovered.stale()).isFalse();
        assertThat(recovered.items().getFirst().volume()).isEqualTo(200);
        assertThat(recovered.fetchedAt()).isNotEqualTo(first.fetchedAt());
    }

    @Test
    void backsOffOnInitialFailureWithoutLeakingUpstreamDetails() {
        when(provider.getVolumeRanking()).thenThrow(new IllegalStateException("private upstream detail"));
        assertThatThrownBy(service::getRanking).isInstanceOf(VolumeRankingUnavailableException.class)
                .hasMessageNotContaining("private");
        assertThatThrownBy(service::getRanking).isInstanceOf(VolumeRankingUnavailableException.class);
        verify(provider, times(1)).getVolumeRanking();
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotReplaceLastGoodRankingWithAnEmptyResponse() {
        when(provider.getVolumeRanking()).thenReturn(List.of(item("005930", 100))).thenReturn(List.of());
        when(repository.findBySymbolIn(anyList())).thenReturn(List.of());
        service.getRanking();
        clock.advance(60);
        var response = service.getRanking();
        assertThat(response.stale()).isTrue();
        assertThat(response.items()).hasSize(1);
    }

    private VolumeRankItem item(String symbol, long volume) {
        return new VolumeRankItem(0, symbol, "종목" + symbol, 70000, new BigDecimal("-1.23"), volume, false);
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
