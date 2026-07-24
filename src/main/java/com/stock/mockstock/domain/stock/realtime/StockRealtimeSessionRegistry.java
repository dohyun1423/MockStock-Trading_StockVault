// 브라우저 WebSocket session을 종목코드별로 관리하고 실시간 메시지를 전달하는 저장소
package com.stock.mockstock.domain.stock.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StockRealtimeSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsBySymbol = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByEmail = new ConcurrentHashMap<>();

    // 특정 종목을 구독하는 브라우저 session을 등록한다.
    public void subscribe(String symbol, WebSocketSession session) {
        sessionsBySymbol
                .computeIfAbsent(symbol, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    // 로그인 사용자의 주문 알림을 받을 브라우저 session을 등록한다.
    public void subscribeUser(String email, WebSocketSession session) {
        sessionsByEmail
                .computeIfAbsent(email, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    // 연결이 끊긴 브라우저 session을 전체 구독 목록에서 제거한다.
    public void remove(WebSocketSession session) {
        sessionsBySymbol.forEach((symbol, sessions) -> {
            sessions.remove(session);

            if (sessions.isEmpty()) {
                sessionsBySymbol.remove(symbol, sessions);
            }
        });

        sessionsByEmail.forEach((email, sessions) -> {
            sessions.remove(session);

            if (sessions.isEmpty()) {
                sessionsByEmail.remove(email, sessions);
            }
        });
    }

    // 현재 열려 있는 브라우저가 구독 중인 종목코드 목록을 반환한다.
    public Set<String> getSubscribedSymbols() {
        Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();

        sessionsBySymbol.forEach((symbol, sessions) -> {
            sessions.removeIf(currentSession -> !currentSession.isOpen());

            if (sessions.isEmpty()) {
                sessionsBySymbol.remove(symbol, sessions);
                return;
            }

            subscribedSymbols.add(symbol);
        });

        return Set.copyOf(subscribedSymbols);
    }

    // 특정 종목을 구독 중인 브라우저들에게 메시지를 전송한다.
    public void broadcast(String symbol, String message) {
        Set<WebSocketSession> sessions = sessionsBySymbol.get(symbol);

        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        sessions.removeIf(session -> !session.isOpen());

        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.warn("Realtime message send failed. symbol={}, sessionId={}", symbol, session.getId(), e);
            }
        });
    }

    // 특정 사용자에게만 주문 체결 알림 메시지를 전송한다.
    public void broadcastToUser(String email, String message) {
        Set<WebSocketSession> sessions = sessionsByEmail.get(email);

        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        sessions.removeIf(session -> !session.isOpen());

        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.warn("Order notification send failed. email={}, sessionId={}", email, session.getId(), e);
            }
        });
    }
}
