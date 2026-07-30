package com.musinsa.payments.point.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musinsa.payments.point.api.dto.EarnRequest;
import com.musinsa.payments.point.config.PointTimeProperties;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.web.RequestIdFilter;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("운영 관점 - 시간대 / 페이지 상한 / 요청 추적")
class PointOperationalTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointTimeProperties timeProperties;

    @Test
    @DisplayName("만료 기준 시간대는 서버 기본값이 아니라 설정된 업무 시간대를 쓴다")
    void expiryUsesConfiguredBusinessZone() {
        assertThat(timeProperties.zoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(Clock.system(timeProperties.zoneId()).getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("페이지 크기가 상한을 넘으면 400 을 반환한다")
    void pageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/v1/points/transactions")
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/points/transactions")
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("음수 페이지 번호는 400 을 반환한다")
    void negativePageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/points/transactions")
                        .param("userId", String.valueOf(USER_ID))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("요청 헤더의 X-Request-Id 를 응답에 되돌려준다")
    void requestIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/points/balance")
                        .param("userId", String.valueOf(USER_ID))
                        .header(RequestIdFilter.HEADER, "trace-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-1234"));
    }

    @Test
    @DisplayName("X-Request-Id 가 없으면 새로 발급해 응답에 실어준다")
    void requestIdIsGeneratedWhenAbsent() throws Exception {
        String issued = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EarnRequest(USER_ID, 1000L, null, null, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(issued).isNotBlank();
    }
}
