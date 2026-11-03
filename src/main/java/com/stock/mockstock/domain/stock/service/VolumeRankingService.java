package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.VolumeRankItem;
import com.stock.mockstock.domain.stock.dto.VolumeRankingResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.provider.KisVolumeRankingProvider;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VolumeRankingService {
    private static final long REFRESH_SECONDS = 60;
    private final KisVolumeRankingProvider provider;
    private final StockRepository stockRepository;
    private final Clock clock;
    private VolumeRankingResponse cached;
    private Instant nextAttempt = Instant.MIN;
    private String failureMessage = "거래량 순위를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.";

    @Autowired
    public VolumeRankingService(KisVolumeRankingProvider provider, StockRepository stockRepository) {
        this(provider, stockRepository, Clock.systemUTC());
    }

    VolumeRankingService(KisVolumeRankingProvider provider, StockRepository stockRepository, Clock clock) {
        this.provider = provider;
        this.stockRepository = stockRepository;
        this.clock = clock;
    }

    // 사용자/탭 수와 관계없이 서버 전체에서 60초에 한 번만 조회한다. 실패도 같은 간격으로 재시도한다.
    public synchronized VolumeRankingResponse getRanking() {
        if (!clock.instant().isBefore(nextAttempt)) {
            try {
                List<VolumeRankItem> items = buildRanking(provider.getVolumeRanking());
                cached = new VolumeRankingResponse(items, clock.instant().toString(), false, REFRESH_SECONDS);
            } catch (RuntimeException e) {
                failureMessage = e instanceof VolumeRankingUnavailableException
                        ? e.getMessage() : "거래량 순위를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.";
                log.warn("Volume ranking refresh failed. type={}", e.getClass().getSimpleName(), e);
                if (cached != null) {
                    cached = new VolumeRankingResponse(cached.items(), cached.fetchedAt(), true, REFRESH_SECONDS);
                }
            } finally {
                nextAttempt = clock.instant().plusSeconds(REFRESH_SECONDS);
            }
        }
        if (cached == null) {
            throw new VolumeRankingUnavailableException(failureMessage);
        }
        return cached;
    }

    private List<VolumeRankItem> buildRanking(List<VolumeRankItem> input) {
        Map<String, VolumeRankItem> unique = new LinkedHashMap<>();
        input.stream().sorted(Comparator.comparingLong(VolumeRankItem::volume).reversed())
                .forEach(item -> unique.putIfAbsent(item.symbol(), item));
        List<VolumeRankItem> top = unique.values().stream().limit(30).toList();
        if (top.isEmpty()) {
            throw new IllegalStateException("Empty volume ranking");
        }
        Map<String, Stock> stocks = stockRepository.findBySymbolIn(top.stream().map(VolumeRankItem::symbol).toList())
                .stream().collect(Collectors.toMap(Stock::getSymbol, Function.identity()));
        List<VolumeRankItem> result = new ArrayList<>();
        for (VolumeRankItem item : top) {
            Stock stock = stocks.get(item.symbol());
            // 순위는 보존하고, 마스터에 없는 종목만 상세/관심종목 동작을 비활성화한다.
            result.add(new VolumeRankItem(result.size() + 1, item.symbol(),
                    stock == null ? item.name() : stock.getName(), item.currentPrice(),
                    item.changeRate(), item.volume(), stock != null));
        }
        return List.copyOf(result);
    }
}
