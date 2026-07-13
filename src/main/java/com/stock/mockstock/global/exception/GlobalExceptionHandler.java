// 전역 예외를 공통 에러 응답으로 변환하는 핸들러
package com.stock.mockstock.global.exception;

import com.stock.mockstock.global.response.ErrorResponse;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
