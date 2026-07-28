package com.musinsa.payments.point;

import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("애플리케이션 컨텍스트")
class FreePointSystemApplicationTests extends IntegrationTestSupport {

    @Test
    @DisplayName("기동 시 포인트 정책이 시딩된다")
    void contextLoadsWithSeededPolicy() {
        assertThat(policyService.findPolicy()).isNotNull();
    }
}
