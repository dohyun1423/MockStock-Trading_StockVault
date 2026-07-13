package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.dto.OrderRequest;
import com.stock.mockstock.domain.order.dto.OrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderUpdateRequest;
import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.portfolio.repository.HoldingRepository;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final List<StockOrderStatus> OPEN_ORDER_STATUSES = List.of(
            StockOrderStatus.PENDING,
            StockOrderStatus.PARTIALLY_FILLED
    );

    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockOrderRepository stockOrderRepository;
    private final HoldingRepository holdingRepository;
    private final StockQuoteService stockQuoteService;
    private final MarketSessionService marketSessionService;
    private final OpenOrderRealtimeSubscriptionService openOrderRealtimeSubscriptionService;

    // 매수 주문을 접수한다.
    public OrderResponse buy(String email, OrderRequest request) {
        return placeOrder(email, request, OrderType.BUY);
    }

    // 매도 주문을 접수한다.
    public OrderResponse sell(String email, OrderRequest request) {
        return placeOrder(email, request, OrderType.SELL);
    }

    // 로그인 사용자의 아직 끝나지 않은 미체결/부분체결 주문을 조회한다.
    @Transactional(readOnly = true)
    public List<StockOrderResponse> getMyOpenOrders(String email) {
        User user = getUser(email);

        return stockOrderRepository.findAllByUserAndStatusInOrderByCreatedAtDesc(user, OPEN_ORDER_STATUSES)
                .stream()
                .map(StockOrderResponse::from)
                .toList();
    }

    // 사용자가 직접 미체결/부분체결 주문을 취소하고 묶여 있던 현금 또는 수량을 해제한다.
    public StockOrderResponse cancelOrder(String email, Long orderId) {
        User user = getUserForUpdate(email);
        StockOrder stockOrder = getMyCancelableOrderForUpdate(user, orderId);

        releaseReservation(user, stockOrder);
        stockOrder.cancel();

        return StockOrderResponse.from(stockOrder);
    }

    // 미체결/부분체결 주문의 주문가와 남은 수량을 수정하고 예약 금액 또는 예약 수량 차액을 정산한다.
    public StockOrderResponse updateOrder(
            String email,
            Long orderId,
            StockOrderUpdateRequest request
    ) {
        validateOrderUpdateRequest(request);

        User user = getUserForUpdate(email);
        StockOrder stockOrder = getMyCancelableOrderForUpdate(user, orderId);

        if (stockOrder.getOrderType() == OrderType.BUY) {
            updateBuyOrder(user, stockOrder, request);
        } else {
            updateSellOrder(stockOrder, request);
        }

        openOrderRealtimeSubscriptionService.subscribeTradeAfterCommit(stockOrder.getStock().getSymbol());

        return StockOrderResponse.from(stockOrder);
    }

    // 주문 생성, 현금/수량 예약, 즉시 체결 가능 여부 확인을 한 번에 처리한다.
    private OrderResponse placeOrder(
            String email,
            OrderRequest request,
            OrderType orderType
    ) {
        validateOrderRequest(request);

        User user = getUserForUpdate(email);
        Stock stock = getStockBySymbol(request.getSymbol());
        MarketSession session = marketSessionService.getCurrentSession();

        if (!marketSessionService.isOrderAvailable(session)) {
            throw new IllegalArgumentException("현재는 주문할 수 없는 시간입니다.");
        }

        Long currentPrice = getCurrentPrice(stock);
        Long orderPrice = resolveOrderPrice(request, currentPrice);
        Integer quantity = request.getQuantity();

        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                orderType,
                orderPrice,
                quantity,
                session
        );

        reserveOrder(user, stock, stockOrder);
        stockOrderRepository.save(stockOrder);

        openOrderRealtimeSubscriptionService.subscribeTradeAfterCommit(stock.getSymbol());

        return new OrderResponse(
                stock.getName(),
                orderType,
                quantity,
                orderPrice,
                orderPrice * quantity,
                user.getCash(),
                "PENDING",
                session,
                stockOrder.getId(),
                "미체결 주문으로 접수되었습니다."
        );
    }

    // 매수는 현금을, 매도는 보유 수량을 예약 상태로 묶는다.
    private void reserveOrder(
            User user,
            Stock stock,
            StockOrder stockOrder
    ) {
        if (stockOrder.getOrderType() == OrderType.BUY) {
            user.reserveCash(stockOrder.getReservedAmount());
            return;
        }

        Holding holding = holdingRepository.findByUserAndStockForUpdate(user, stock)
                .orElseThrow(() -> new IllegalArgumentException("보유 중인 종목이 아닙니다."));

        holding.reserveQuantity(stockOrder.getReservedQuantity());
    }

    // 주문 취소 시 주문 유형에 맞게 예약 현금 또는 예약 수량을 되돌린다.
    private void releaseReservation(User user, StockOrder stockOrder) {
        if (stockOrder.getOrderType() == OrderType.BUY) {
            user.releaseReservedCash(stockOrder.getRemainingReservedAmount());
            return;
        }

        Holding holding = holdingRepository.findByUserAndStockForUpdate(user, stockOrder.getStock())
                .orElseThrow(() -> new IllegalArgumentException("보유 종목 정보를 찾을 수 없습니다."));

        holding.releaseReservedQuantity(stockOrder.getRemainingReservedQuantity());
    }

    // 매수 주문 수정 시 기존 예약금과 새 예약금의 차액을 정산한다.
    private void updateBuyOrder(
            User user,
            StockOrder stockOrder,
            StockOrderUpdateRequest request
    ) {
        Long oldReservedAmount = stockOrder.getRemainingReservedAmount();
        Long newReservedAmount = calculateOrderAmount(request.getOrderPrice(), request.getRemainingQuantity());

        if (newReservedAmount > oldReservedAmount) {
            user.reserveCash(newReservedAmount - oldReservedAmount);
        } else if (newReservedAmount < oldReservedAmount) {
            user.releaseReservedCash(oldReservedAmount - newReservedAmount);
        }

        stockOrder.updateOpenOrder(request.getOrderPrice(), request.getRemainingQuantity());
    }

    // 매도 주문 수정 시 기존 예약 수량과 새 예약 수량의 차이를 보유 종목에 반영한다.
    private void updateSellOrder(
            StockOrder stockOrder,
            StockOrderUpdateRequest request
    ) {
        Holding holding = holdingRepository.findByUserAndStockForUpdate(stockOrder.getUser(), stockOrder.getStock())
                .orElseThrow(() -> new IllegalArgumentException("보유 종목 정보를 찾을 수 없습니다."));

        Integer oldReservedQuantity = stockOrder.getRemainingReservedQuantity();
        Integer newReservedQuantity = request.getRemainingQuantity();

        if (newReservedQuantity > oldReservedQuantity) {
            holding.reserveQuantity(newReservedQuantity - oldReservedQuantity);
        } else if (newReservedQuantity < oldReservedQuantity) {
            holding.releaseReservedQuantity(oldReservedQuantity - newReservedQuantity);
        }

        stockOrder.updateOpenOrder(request.getOrderPrice(), request.getRemainingQuantity());
    }

    // 주문 수정 요청의 주문가와 남은 수량을 검증한다.
    private void validateOrderUpdateRequest(StockOrderUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 수정 요청이 비어 있습니다.");
        }

        if (request.getOrderPrice() == null || request.getOrderPrice() <= 0) {
            throw new IllegalArgumentException("주문 가격은 0보다 커야 합니다.");
        }

        if (request.getRemainingQuantity() == null || request.getRemainingQuantity() <= 0) {
            throw new IllegalArgumentException("미체결 수량은 1주 이상이어야 합니다.");
        }
    }

    // 주문가와 수량으로 예약 금액을 계산한다.
    private Long calculateOrderAmount(Long orderPrice, Integer quantity) {
        try {
            return Math.multiplyExact(orderPrice, quantity.longValue());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("주문 금액이 너무 큽니다.");
        }
    }

    // 주문 가격이 없으면 현재가를 기본 주문 가격으로 사용한다.
    private Long resolveOrderPrice(OrderRequest request, Long currentPrice) {
        if (request.getOrderPrice() != null && request.getOrderPrice() > 0) {
            return request.getOrderPrice();
        }

        return currentPrice;
    }

    // 주문 요청의 필수값과 가격, 수량을 검증한다.
    private void validateOrderRequest(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 요청이 비어 있습니다.");
        }

        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("종목코드는 필수입니다.");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("수량은 1주 이상이어야 합니다.");
        }

        if (request.getOrderPrice() != null && request.getOrderPrice() <= 0) {
            throw new IllegalArgumentException("주문 가격은 0보다 커야 합니다.");
        }
    }

    // 로그인 사용자를 email 기준으로 조회한다.
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    // 현금 변경이 필요한 주문 흐름에서는 사용자 정보를 쓰기 락으로 조회한다.
    private User getUserForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    // 로그인 사용자의 취소/수정 가능한 주문인지 확인하고 쓰기 락으로 조회한다.
    private StockOrder getMyCancelableOrderForUpdate(User user, Long orderId) {
        StockOrder stockOrder = stockOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문입니다."));

        if (!stockOrder.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인의 주문만 처리할 수 있습니다.");
        }

        if (!stockOrder.isCancelable()) {
            throw new IllegalArgumentException("이미 처리되어 변경할 수 없는 주문입니다.");
        }

        return stockOrder;
    }

    // 주문 대상 종목을 symbol 기준으로 조회한다.
    private Stock getStockBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        return stockRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));
    }

    // 종목코드 비교와 조회를 위해 공백 제거 및 대문자 변환을 수행한다.
    private String normalizeSymbol(String symbol) {
        return symbol.trim().replaceAll("\\s+", "").toUpperCase();
    }

    // 주문 기준이 되는 현재가를 KIS quote provider 기준으로 조회한다.
    private Long getCurrentPrice(Stock stock) {
        StockQuoteResponse quote = stockQuoteService.getQuote(stock.getSymbol());

        if (quote == null || quote.getCurrentPrice() == null || quote.getCurrentPrice() <= 0) {
            throw new IllegalArgumentException("현재가 정보를 가져올 수 없습니다.");
        }

        return quote.getCurrentPrice();
    }
}
