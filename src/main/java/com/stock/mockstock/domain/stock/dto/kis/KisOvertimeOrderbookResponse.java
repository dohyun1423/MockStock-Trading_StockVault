// KIS 국내주식 시간외호가 API 응답 값을 매핑하는 DTO다.
package com.stock.mockstock.domain.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOvertimeOrderbookResponse {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    private String msg1;

    private Map<String, String> output;

    @JsonProperty("output1")
    private Map<String, String> output1;

    // KIS 문서와 실제 응답의 output 키 차이를 흡수해 사용할 호가 데이터를 반환한다.
    public Map<String, String> getEffectiveOutput() {
        if (output != null && !output.isEmpty()) {
            return output;
        }

        return output1;
    }
}
