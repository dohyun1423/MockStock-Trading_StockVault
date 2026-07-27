// 애플리케이션 예외를 사용자에게 안전한 공통 오류 응답으로 변환하는 전역 처리기다.
package com.stock.mockstock.global.exception;

import com.stock.mockstock.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스에서 발생한 잘못된 요청을 사용자에게 이해할 수 있는 400 응답으로 변환한다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        String errorId = resolveErrorId();
        log.warn("Business request rejected. errorId={}, message={}", errorId, e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, safeBusinessMessage(e.getMessage()), errorId);
    }

    // DTO 검증 실패 시 첫 번째 검증 메시지를 400 응답으로 반환한다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorId = resolveErrorId();
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? "입력값을 다시 확인해 주세요."
                : fieldError.getDefaultMessage();
        log.warn(
                "Request validation failed. errorId={}, field={}",
                errorId,
                fieldError == null ? "unknown" : fieldError.getField()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, message, errorId);
    }

    // 요청 파라미터와 경로 변수의 형식 오류를 공통 400 응답으로 변환한다.
    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(Exception e) {
        String errorId = resolveErrorId();
        log.warn("Invalid request format. errorId={}, exception={}", errorId, e.getClass().getSimpleName());
        return buildResponse(HttpStatus.BAD_REQUEST, "요청 형식이나 입력값을 다시 확인해 주세요.", errorId);
    }

    // 주문 상태가 동시에 변경된 경우 새로고침 후 재시도할 수 있는 409 응답을 반환한다.
    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class
    })
    public ResponseEntity<ErrorResponse> handleOrderConcurrencyException(RuntimeException e) {
        String errorId = resolveErrorId();
        log.warn("Concurrent update conflict. errorId={}, exception={}", errorId, e.getClass().getSimpleName());
        return buildResponse(
                HttpStatus.CONFLICT,
                "주문 상태가 변경되었습니다. 다시 조회한 뒤 시도해 주세요.",
                errorId
        );
    }

    // DB 제약 조건 오류의 상세 SQL을 숨기고 사용자용 409 응답만 반환한다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        String errorId = resolveErrorId();
        log.error("Database integrity violation. errorId={}", errorId, e);
        return buildResponse(
                HttpStatus.CONFLICT,
                "요청 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                errorId
        );
    }

    // 예상하지 못한 서버 오류의 상세 내용은 로그에만 남기고 일반화된 500 응답을 반환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        String errorId = resolveErrorId();
        log.error("Unexpected server error. errorId={}", errorId, e);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                errorId
        );
    }

    // 내부 구현 정보가 포함될 수 있는 비정상 메시지를 사용자에게 노출하지 않도록 정리한다.
    private String safeBusinessMessage(String message) {
        if (message == null || message.isBlank()) {
            return "요청을 처리할 수 없습니다. 입력값을 다시 확인해 주세요.";
        }

        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("sql")
                || lowerMessage.contains("hibernate")
                || lowerMessage.contains("jdbc")
                || lowerMessage.contains("exception")
                || lowerMessage.contains("could not execute")) {
            return "요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        }
        return message;
    }

    // 현재 요청의 추적 ID를 가져오고 필터 밖에서 발생한 예외라면 임시 ID를 생성한다.
    private String resolveErrorId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    // 상태 코드와 사용자 메시지를 공통 오류 응답 형태로 조립한다.
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String errorId) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, errorId));
    }
}
