// 관심종목 추가 시 종목명의 필수값과 최대 길이를 검증하는 요청 DTO다.
package com.stock.mockstock.domain.watchlist.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WatchlistCreateRequest {

    @NotBlank(message = "종목명은 필수입니다.")
    @Size(max = ApplicationPolicy.MAX_STOCK_NAME_LENGTH, message = "종목명이 너무 깁니다.")
    private String stockName;
}
