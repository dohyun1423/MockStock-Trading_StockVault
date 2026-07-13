// 종목 검색, 상세, 현재가 API 요청을 받는 컨트롤러
package com.stock.mockstock.domain.stock.controller;

import com.stock.mockstock.domain.stock.dto.StockDetailResponse;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.dto.StockSearchResponse;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import com.stock.mockstock.domain.stock.service.StockService;
import com.stock.mockstock.domain.stock.dto.StockPriceHistoryResponse;
import com.stock.mockstock.domain.stock.service.StockPriceHistoryService;
import com.stock.mockstock.domain.stock.dto.OrderbookResponse;
import com.stock.mockstock.domain.stock.service.OrderbookService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final StockQuoteService stockQuoteService;
    private final StockPriceHistoryService stockPriceHistoryService;
    private final OrderbookService orderbookService;

    // 키워드로 종목 검색
    @GetMapping("/search")
    public List<StockSearchResponse> searchStocks(@RequestParam String keyword) {
        return stockService.searchStocks(keyword);
    }

    // 종목 id로 상세 정보 조회
    @GetMapping("/id/{stockId}")
    public StockDetailResponse getStockDetail(@PathVariable Long stockId) {
        return stockService.getStockDetail(stockId);
    }

    // 종목명으로 상세 정보 조회
    @GetMapping("/detail")
    public StockDetailResponse getStockDetailByName(@RequestParam String name) {
        return stockService.getStockDetailByName(name);
    }

    // 종목 코드로 상세 정보 조회
    @GetMapping("/symbol/{symbol}")
    public StockDetailResponse getStockDetailBySymbol(@PathVariable String symbol) {
        return stockService.getStockDetailBySymbol(symbol);
    }

    // 종목 코드로 현재가 정보 조회
    @GetMapping("/{symbol}/quote")
    public StockQuoteResponse getStockQuote(@PathVariable String symbol) {
        return stockQuoteService.getQuote(symbol);
    }

    // 종목코드와 기간으로 차트용 가격 이력 조회
    @GetMapping("/{symbol}/prices")
    public List<StockPriceHistoryResponse> getStockPriceHistories(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") String period
    ) {
        return stockPriceHistoryService.getPriceHistories(symbol, period);
    }

    // 종목코드 기준 호가 정보 조회
    @GetMapping("/{symbol}/orderbook")
    public OrderbookResponse getStockOrderbook(@PathVariable String symbol) {
        return orderbookService.getOrderbook(symbol);
    }

}
