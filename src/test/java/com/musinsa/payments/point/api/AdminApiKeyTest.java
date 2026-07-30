package com.musinsa.payments.point.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musinsa.payments.point.api.dto.EarnRequest;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.security.AdminApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("관리자 API 인증")
class AdminApiKeyTest extends IntegrationTestSupport {

    private static final String VALID_KEY = "local-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("키가 없으면 401 이다")
    void rejectsMissingKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/points/policies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("키가 틀리면 401 이다")
    void rejectsWrongKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/points/policies")
                        .header(AdminApiKeyFilter.HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("수기지급도 키 없이는 막힌다")
    void rejectsManualEarnWithoutKey() throws Exception {
        mockMvc.perform(post("/api/v1/admin/points/earn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EarnRequest(USER_ID, 1000L, null, null, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("올바른 키면 통과한다")
    void acceptsValidKey() throws Exception {
        mockMvc.perform(get("/api/v1/admin/points/policies")
                        .header(AdminApiKeyFilter.HEADER, VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사용자 API 는 관리자 키 없이 동작한다")
    void userApiIsNotGuardedByAdminKey() throws Exception {
        mockMvc.perform(get("/api/v1/points/balance").param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isOk());
    }
}
