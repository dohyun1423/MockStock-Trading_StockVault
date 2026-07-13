// 내정보 화면에서 닉네임 변경 요청 값을 받는 DTO
package com.stock.mockstock.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class NicknameUpdateRequest {

    @NotBlank
    @Size(min = 2, max = 16)
    private String nickname;
}
