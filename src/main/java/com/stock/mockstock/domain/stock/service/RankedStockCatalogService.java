package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.StockDetailResponse;
import com.stock.mockstock.domain.stock.dto.VolumeRankItem;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.provider.KisStockCatalogProvider;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
public class RankedStockCatalogService {
    private final StockRepository repository;
    private final VolumeRankingService rankings;
    private final KisStockCatalogProvider provider;
    private final TransactionTemplate transactions;

    public RankedStockCatalogService(StockRepository repository, VolumeRankingService rankings,
                                     KisStockCatalogProvider provider, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rankings = rankings;
        this.provider = provider;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public synchronized StockDetailResponse resolve(String symbol) {
        String code = symbol.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("(?:[0-9A-Z]{6}|Q[0-9]{6})")) {
            throw new IllegalArgumentException("종목코드를 확인해 주세요.");
        }
        Stock existing = repository.findBySymbol(code).orElse(null);
        if (existing != null) return StockDetailResponse.from(existing);

        // 이름/가격을 클라이언트에서 받지 않고 서버의 KIS 순위로 검증한다.
        VolumeRankItem ranked = rankings.getRanking().items().stream().filter(item -> item.symbol().equals(code))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("탐색 목록을 새로고침한 뒤 다시 선택해 주세요."));
        Stock candidate;
        try {
            var quote = provider.getCatalogQuote(code);
            String returnedCode = quote.symbol() == null ? "" : quote.symbol().trim();
            if (!returnedCode.equals(code) && !(code.startsWith("Q") && returnedCode.equals(code.substring(1)))) {
                throw new IllegalStateException("Catalog quote symbol mismatch");
            }
            long price = Long.parseLong(required(quote.price()));
            long shares = Long.parseLong(required(quote.listedShares()));
            long volume = Long.parseLong(required(quote.volume()));
            if (price <= 0 || shares <= 0 || volume < 0) throw new IllegalStateException("Invalid catalog quote");
            candidate = Stock.builder().symbol(code).name(ranked.name()).market(required(quote.market()))
                    .sector(quote.sector() == null || quote.sector().isBlank() ? "미분류" : quote.sector().trim())
                    .currentPrice(price).changePrice(Long.parseLong(required(quote.changePrice())))
                    .changeRate(new BigDecimal(required(quote.changeRate()))).volume(volume)
                    .listedShares(shares).marketCap(Math.multiplyExact(price, shares))
                    .per(optionalNumber(quote.per())).eps(optionalNumber(quote.eps())).dividendYield(null).build();
        } catch (RuntimeException error) {
            log.warn("Ranked stock metadata lookup failed. symbol={}, type={}", code, error.getClass().getSimpleName());
            throw new VolumeRankingUnavailableException("종목 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        // 외부 조회를 마친 뒤 짧은 트랜잭션으로 저장하며 여러 서버의 중복 등록도 처리한다.
        try {
            Stock stock = transactions.execute(status -> repository.findBySymbol(code)
                    .orElseGet(() -> repository.saveAndFlush(candidate)));
            return StockDetailResponse.from(stock);
        } catch (DataIntegrityViolationException conflict) {
            return repository.findBySymbol(code).map(StockDetailResponse::from).orElseThrow(() -> conflict);
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing catalog quote field");
        return value.trim().replace(",", "");
    }

    private BigDecimal optionalNumber(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(required(value));
    }
}
