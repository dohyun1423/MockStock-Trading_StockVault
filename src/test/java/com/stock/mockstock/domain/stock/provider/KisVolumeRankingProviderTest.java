package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.kis.KisTokenService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import com.stock.mockstock.global.config.KisProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KisVolumeRankingProviderTest {
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> trId = new AtomicReference<>();
    private final KisTokenService tokens = mock(KisTokenService.class);
    private final KisProperties properties = new KisProperties();
    private KisVolumeRankingProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/uapi/domestic-stock/v1/quotations/volume-rank", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            trId.set(exchange.getRequestHeaders().getFirst("tr_id"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        properties.setProvider("kis");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAppKey("test-key");
        properties.setAppSecret("test-secret");
        when(tokens.getAccessToken()).thenReturn("test-token");
        provider = new KisVolumeRankingProvider(properties, tokens, RestClient.builder());
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void requestsEntireKrxMarketAndParsesCodesPricesAndSignedRates() {
        body.set("""
                {"rt_cd":"0","ignored":"value","output":[{
                  "mksc_shrn_iscd":"005930","hts_kor_isnm":"삼성전자",
                  "stck_prpr":"70,000","prdy_ctrt":"-1.25","acml_vol":"1,234,567",
                  "data_rank":"1"}]}
                """);
        var items = provider.getVolumeRanking();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().symbol()).isEqualTo("005930");
        assertThat(items.getFirst().name()).isEqualTo("삼성전자");
        assertThat(items.getFirst().currentPrice()).isEqualTo(70000);
        assertThat(items.getFirst().changeRate()).isEqualByComparingTo("-1.25");
        assertThat(items.getFirst().volume()).isEqualTo(1234567);
        assertThat(query.get()).contains("FID_INPUT_ISCD=0000", "FID_COND_MRKT_DIV_CODE=J", "FID_BLNG_CLS_CODE=0");
        assertThat(trId.get()).isEqualTo("FHPST01710000");
    }

    @Test
    void acceptsRealKisMixedStockEtfAndEtnCodesWithoutDroppingTheRanking() {
        // 실제 KIS 응답에서 발견한 코드 형식과 int 범위를 넘는 누적 거래량이다.
        body.set("""
                {"rt_cd":"0","output":[
                 {"mksc_shrn_iscd":"252670","hts_kor_isnm":"KODEX 200선물인버스2X",
                  "stck_prpr":"100","prdy_ctrt":"7.53","acml_vol":"8883279720"},
                 {"mksc_shrn_iscd":"0147B0","hts_kor_isnm":"래블업",
                  "stck_prpr":"14220","prdy_ctrt":"-25.16","acml_vol":"37324149"},
                 {"mksc_shrn_iscd":"Q530107","hts_kor_isnm":"삼성 인버스 2X 코스닥150 선물 ETN",
                  "stck_prpr":"2300","prdy_ctrt":"9.79","acml_vol":"12531266"}]}
                """);
        var items = provider.getVolumeRanking();
        assertThat(items).extracting(item -> item.symbol()).containsExactly("252670", "0147B0", "Q530107");
        assertThat(items.getFirst().volume()).isEqualTo(8_883_279_720L);
        assertThat(items.getLast().symbol()).isEqualTo("Q530107");
    }

    @Test
    void rejectsKisBusinessErrorsAndEmptyOutput() {
        body.set("{\"rt_cd\":\"1\",\"output\":[]}");
        assertThatThrownBy(provider::getVolumeRanking).isInstanceOf(VolumeRankingUnavailableException.class);
        body.set("{\"rt_cd\":\"0\",\"output\":[]}");
        assertThatThrownBy(provider::getVolumeRanking).isInstanceOf(VolumeRankingUnavailableException.class);
    }

    @Test
    void doesNotReturnInventedRankingsInDummyMode() {
        properties.setProvider("dummy");
        assertThatThrownBy(provider::getVolumeRanking).isInstanceOf(VolumeRankingUnavailableException.class)
                .hasMessageContaining("KIS");
        verify(tokens, never()).getAccessToken();
    }

    @Test
    void rejectsMissingNumbersInsteadOfDisplayingZeroAsLiveData() {
        body.set("""
                {"rt_cd":"0","output":[{"mksc_shrn_iscd":"005930","hts_kor_isnm":"삼성전자",
                "stck_prpr":"70000","prdy_ctrt":"","acml_vol":"100"}]}
                """);
        assertThatThrownBy(provider::getVolumeRanking).isInstanceOf(IllegalStateException.class);
    }
}
