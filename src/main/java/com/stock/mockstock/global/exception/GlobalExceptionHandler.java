// 전역 예외를 공통 에러 응답으로 변환하는 핸들러
package com.stock.mockstock.global.exception;

import com.stock.mockstock.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    // IllegalArgumentException을 400 응답으로 변환
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e
    ) {

        ErrorResponse response = new ErrorResponse(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class
    })
    // 주문 수정/취소/체결이 동시에 발생한 경우에는 재시도 가능한 충돌 응답으로 변환한다.
    public ResponseEntity<ErrorResponse> handleOrderConcurrencyException(
            RuntimeException e
    ) {

        ErrorResponse response = new ErrorResponse("주문 상태가 변경되었습니다. 다시 조회한 뒤 시도해 주세요.");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    // DB 제약조건 또는 컬럼 불일치 오류의 내부 SQL을 숨기고 사용자용 메시지만 반환한다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException e
    ) {
        log.error("Database integrity violation occurred while processing a request.", e);

        ErrorResponse response = new ErrorResponse(
                "요청한 정보를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}
