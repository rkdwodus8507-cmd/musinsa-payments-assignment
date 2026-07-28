package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.PointCommands;
import com.musinsa.payments.point.service.dto.PointResults;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("포인트 정책 (하드코딩 없이 변경 가능)")
class PointPolicyServiceTest extends IntegrationTestSupport {

    @Test
    @DisplayName("설정 파일 값으로 정책이 초기화된다")
    void policyIsSeededFromProperties() {
        PointResults.Policy policy = policyService.findPolicy();

        assertThat(policy.minEarnAmount()).isEqualTo(1);
        assertThat(policy.maxEarnAmount()).isEqualTo(100_000);
        assertThat(policy.maxUserBalance()).isEqualTo(1_000_000);
        assertThat(policy.defaultExpireDays()).isEqualTo(365);
    }

    @Test
    @DisplayName("1회 최대 적립금액을 변경하면 재기동 없이 즉시 반영된다")
    void maxEarnAmountIsChangeableAtRuntime() {
        assertThatThrownBy(() -> earn(200_000, null)).isInstanceOf(PointException.class);

        policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 200_000, 1_000_000, 365, 1, 1824));

        assertThat(earn(200_000, null).amount()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("개인별 최대 보유금액을 변경하면 재기동 없이 즉시 반영된다")
    void maxUserBalanceIsChangeableAtRuntime() {
        policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 100_000, 150_000, 365, 1, 1824));

        earn(100_000, null);
        assertErrorCode(() -> earn(100_000, null), ErrorCode.MAX_BALANCE_EXCEEDED);
        assertThat(earn(50_000, null).balance()).isEqualTo(150_000);
    }

    @Test
    @DisplayName("기본 만료일을 변경하면 이후 적립부터 적용된다")
    void defaultExpireDaysIsChangeableAtRuntime() {
        policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 100_000, 1_000_000, 30, 1, 1824));

        assertThat(earn(1000, null).expireAt())
                .isEqualTo(clock.currentDateTime().plusDays(30));
    }

    @Test
    @DisplayName("최대 만료일은 5년 미만까지만 설정할 수 있다")
    void maxExpireDaysCannotReachFiveYears() {
        assertErrorCode(() -> policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 100_000, 1_000_000, 365, 1, 1825)), ErrorCode.INVALID_POLICY);
    }

    @Test
    @DisplayName("최대 보유금액은 1회 최대 적립금액보다 작을 수 없다")
    void maxUserBalanceMustCoverMaxEarnAmount() {
        assertErrorCode(() -> policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 100_000, 50_000, 365, 1, 1824)), ErrorCode.INVALID_POLICY);
    }

    private void assertErrorCode(Runnable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
