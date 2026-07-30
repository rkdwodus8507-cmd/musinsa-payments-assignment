package com.musinsa.payments.point.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musinsa.payments.point.api.dto.CancelUseRequest;
import com.musinsa.payments.point.api.dto.EarnRequest;
import com.musinsa.payments.point.api.dto.UpdatePolicyRequest;
import com.musinsa.payments.point.api.dto.UseRequest;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("포인트 API")
class PointApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("적립 → 사용 → 사용취소 흐름이 API로 동작한다")
    void earnUseCancelFlow() throws Exception {
        String earnResponse = postEarn(new EarnRequest(USER_ID, 1000, null, "이벤트 적립", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.manual").value(false))
                .andReturn().getResponse().getContentAsString();
        String earnPointKey = readPointKey(earnResponse);

        mockMvc.perform(get("/api/v1/points/balance").param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000));

        String useResponse = postUse(new UseRequest(USER_ID, "A1234", 600, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400))
                .andExpect(jsonPath("$.details[0].earnPointKey").value(earnPointKey))
                .andReturn().getResponse().getContentAsString();
        String usePointKey = readPointKey(useResponse);

        mockMvc.perform(get("/api/v1/points/orders/{orderId}/usages", "A1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(600));

        mockMvc.perform(post("/api/v1/points/use/{pointKey}/cancel", usePointKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CancelUseRequest(200, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600))
                .andExpect(jsonPath("$.remainingCancelableAmount").value(400));

        mockMvc.perform(get("/api/v1/points/transactions").param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("관리자 수기지급 포인트가 우선 사용된다")
    void manualEarnIsUsedFirst() throws Exception {
        postEarn(new EarnRequest(USER_ID, 1000, 10, null, null))
                .andExpect(status().isOk());

        String manualResponse = postAdminEarn(new EarnRequest(USER_ID, 500, 300, "보상 지급", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manual").value(true))
                .andReturn().getResponse().getContentAsString();

        postUse(new UseRequest(USER_ID, "A1234", 500, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].earnPointKey").value(readPointKey(manualResponse)))
                .andExpect(jsonPath("$.details[0].manual").value(true));
    }

    @Test
    @DisplayName("정책 변경 API로 1회 최대 적립금액을 조정할 수 있다")
    void updatePolicyThroughApi() throws Exception {
        postEarn(new EarnRequest(USER_ID, 200_000, null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EARN_AMOUNT"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/admin/points/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdatePolicyRequest(1, 200_000, 1_000_000, 365, 1, 1824))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxEarnAmount").value(200_000));

        postEarn(new EarnRequest(USER_ID, 200_000, null, null, null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 요청은 400과 에러코드를 반환한다")
    void invalidRequestReturnsBadRequest() throws Exception {
        postUse(new UseRequest(USER_ID, "", 0, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/points/earn/{pointKey}/cancel", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 500이 아니라 404를 반환한다")
    void unmappedPathReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/points/use//cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CancelUseRequest(100, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("본문이 깨진 요청은 400을 반환한다")
    void malformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ResultActions postEarn(EarnRequest request) throws Exception {
        return postJson("/api/v1/points/earn", request);
    }

    private ResultActions postAdminEarn(EarnRequest request) throws Exception {
        return postJson("/api/v1/admin/points/earn", request);
    }

    private ResultActions postUse(UseRequest request) throws Exception {
        return postJson("/api/v1/points/use", request);
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String readPointKey(String response) throws Exception {
        return objectMapper.readTree(response).get("pointKey").asText();
    }
}
