package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.VolumeRankItem;
import com.stock.mockstock.domain.stock.dto.VolumeRankingResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.provider.KisStockCatalogProvider;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RankedStockCatalogServiceTest {
    private final StockRepository repository = mock(StockRepository.class);
    private final VolumeRankingService rankings = mock(VolumeRankingService.class);
    private final KisStockCatalogProvider provider = mock(KisStockCatalogProvider.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private RankedStockCatalogService service;

    @BeforeEach
    void setUp() {
        service = new RankedStockCatalogService(repository, rankings, provider, transactions);
    }

    @Test
    void existingStockUsesCanonicalDatabaseNameWithoutExternalRequestsOrWrites() {
        when(repository.findBySymbol("005930")).thenReturn(Optional.of(
                Stock.builder().symbol("005930").name("삼성전자").build()));
        assertThat(service.resolve("005930").getName()).isEqualTo("삼성전자");
        verifyNoInteractions(rankings, provider, transactions);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void onlyRegistersVerifiedRankedSymbolsWithRealQuoteMetadata() {
        stubMissingEtn();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var stock = service.resolve("Q530107");
        assertThat(stock.getSymbol()).isEqualTo("Q530107");
        assertThat(stock.getName()).isEqualTo("삼성 ETN");
        assertThat(stock.getMarket()).isEqualTo("ETN");
        assertThat(stock.getCurrentPrice()).isEqualTo(2300);
        assertThat(stock.getListedShares()).isEqualTo(60_000_000);
        assertThat(stock.getMarketCap()).isEqualTo(138_000_000_000L);
        assertThat(stock.getChangeRate()).isEqualByComparingTo("9.79");
        assertThat(stock.getPer()).isNull();
        verify(transactions).commit(any());
    }

    @Test
    void unrankedSymbolsCannotCreateArbitraryCatalogEntries() {
        when(repository.findBySymbol("123456")).thenReturn(Optional.empty());
        when(rankings.getRanking()).thenReturn(new VolumeRankingResponse(List.of(), "now", false, 60));
        assertThatThrownBy(() -> service.resolve("123456")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider, transactions);
    }

    @Test
    void failedOrMismatchedQuotesDoNotWriteFakeMetadata() {
        stubMissingEtn();
        when(provider.getCatalogQuote("Q530107")).thenReturn(new KisStockCatalogProvider.Output(
                "999999", "ETN", "", "2300", "205", "9.79", "12531266", "60000000", "", ""));
        assertThatThrownBy(() -> service.resolve("Q530107")).isInstanceOf(VolumeRankingUnavailableException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(transactions, never()).getTransaction(any());
    }

    @Test
    void concurrentRegistrationOnAnotherServerReusesTheCommittedStock() {
        stubMissingEtn();
        Stock winner = Stock.builder().symbol("Q530107").name("삼성 ETN").id(17L).build();
        when(repository.findBySymbol("Q530107")).thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate symbol"));
        assertThat(service.resolve("Q530107").getId()).isEqualTo(17L);
        verify(transactions).rollback(any());
    }

    private void stubMissingEtn() {
        when(repository.findBySymbol("Q530107")).thenReturn(Optional.empty());
        when(rankings.getRanking()).thenReturn(new VolumeRankingResponse(List.of(
                new VolumeRankItem(1, "Q530107", "삼성 ETN", 2300, new BigDecimal("9.79"), 12531266, false)), "now", false, 60));
        when(provider.getCatalogQuote("Q530107")).thenReturn(new KisStockCatalogProvider.Output(
                "530107", "ETN", "", "2300", "205", "9.79", "12531266", "60000000", "", ""));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }
}
