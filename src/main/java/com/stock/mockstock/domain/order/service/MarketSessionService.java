// 현재 한국 거래 시간을 기준으로 주문 가능 세션을 판단하는 서비스다.
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.dto.MarketSessionResponse;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class MarketSessionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    // 서버 시간을 기준으로 현재 거래 세션을 판단한다.
    public MarketSession getCurrentSession() {
        LocalTime now = ZonedDateTime.now(KOREA_ZONE).toLocalTime();

        if (isBetween(now, "08:00", "08:40")) {
            return MarketSession.PRE_MARKET;
        }

        if (isBetween(now, "08:40", "09:00")) {
            return MarketSession.OPENING_AUCTION;
        }

        if (isBetween(now, "09:00", "15:20")) {
            return MarketSession.REGULAR;
        }

        if (isBetween(now, "15:20", "15:30")) {
            return MarketSession.CLOSING_AUCTION;
        }

        if (isBetween(now, "15:30", "15:40")) {
            return MarketSession.AFTER_MARKET_WAIT;
        }

        if (isBetween(now, "15:40", "16:00")) {
            return MarketSession.AFTER_MARKET_CLOSING_PRICE;
        }

        if (isBetween(now, "16:00", "18:00")) {
            return MarketSession.AFTER_HOURS_SINGLE_PRICE;
        }

        if (isBetween(now, "18:00", "20:00")) {
            return MarketSession.RESERVATION;
        }

        return MarketSession.CLOSED;
    }

    // 주문 모달에서 사용할 현재 거래 세션 정보를 만든다.
    public MarketSessionResponse getCurrentSessionResponse() {
        MarketSession session = getCurrentSession();

        return new MarketSessionResponse(
                session,
                getDisplayName(session),
                isOrderAvailable(session),
                isImmediateExecution(session),
                isReservationAvailable(session),
                getMessage(session)
        );
    }

    // 주문 접수 자체가 가능한 세션인지 확인한다.
    public boolean isOrderAvailable(MarketSession session) {
        return session != MarketSession.CLOSED;
    }

    // 가격 조건이 맞으면 즉시 체결을 시도할 수 있는 세션인지 확인한다.
    public boolean isImmediateExecution(MarketSession session) {
        return session == MarketSession.REGULAR
                || session == MarketSession.AFTER_MARKET_CLOSING_PRICE
                || session == MarketSession.AFTER_HOURS_SINGLE_PRICE;
    }

    // 즉시 체결 대신 미체결/예약 주문으로 접수 가능한 세션인지 확인한다.
    public boolean isReservationAvailable(MarketSession session) {
        return session == MarketSession.PRE_MARKET
                || session == MarketSession.OPENING_AUCTION
                || session == MarketSession.CLOSING_AUCTION
                || session == MarketSession.AFTER_MARKET_WAIT
                || session == MarketSession.RESERVATION;
    }

    // KIS WebSocket 대신 시간외 REST 폴링을 사용해야 하는 세션인지 확인한다.
    public boolean isAfterMarketPollingSession(MarketSession session) {
        return session == MarketSession.AFTER_MARKET_WAIT
                || session == MarketSession.AFTER_MARKET_CLOSING_PRICE;
    }

    // 거래 세션 코드를 사용자에게 보여줄 이름으로 변환한다.
    public String getDisplayName(MarketSession session) {
        return switch (session) {
            case PRE_MARKET -> "장전 주문";
            case OPENING_AUCTION -> "장 시작 동시호가";
            case REGULAR -> "정규장";
            case CLOSING_AUCTION -> "장 마감 동시호가";
            case AFTER_MARKET_WAIT -> "장후 대기";
            case AFTER_MARKET_CLOSING_PRICE -> "장후 시간외";
            case AFTER_HOURS_SINGLE_PRICE -> "시간외 단일가";
            case RESERVATION -> "예약 주문";
            case CLOSED -> "거래 종료";
        };
    }

    // 거래 세션에 맞는 사용자 안내 문구를 만든다.
    private String getMessage(MarketSession session) {
        if (isImmediateExecution(session)) {
            return "가격 조건이 맞으면 즉시 체결됩니다.";
        }

        if (isReservationAvailable(session)) {
            return "현재 주문은 미체결/예약 주문으로 접수됩니다.";
        }

        return "현재는 주문할 수 없는 시간입니다.";
    }

    // 현재 시간이 시작 시간 이상, 종료 시간 미만인지 확인한다.
    private boolean isBetween(LocalTime now, String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);

        return !now.isBefore(startTime) && now.isBefore(endTime);
    }
}
