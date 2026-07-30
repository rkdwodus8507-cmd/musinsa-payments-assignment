package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointPolicyValues;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("동시성 - 사용자 단위 비관적 락")
class PointConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 10;

    @Test
    @DisplayName("잔액 500 상태에서 100포인트 사용을 10건 동시 요청하면 정확히 5건만 성공한다")
    void concurrentUseDoesNotOverspend() {
        earn(500, null);

        ConcurrentRun<?> run = runConcurrently(THREAD_COUNT,
                index -> useService.use(new UseCommand(USER_ID, "ORDER-" + index, 100, null)));

        assertThat(run.successCount()).isEqualTo(5);
        assertThat(run.failureCount()).isEqualTo(5);
        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(earnedPointRepository.findByUserIdOrderByIdAsc(USER_ID))
                .allSatisfy(earnedPoint -> assertThat(earnedPoint.getRemainingAmount()).isNotNegative());
    }

    @Test
    @DisplayName("최대 보유금액 근처에서 동시 적립해도 한도를 넘지 않는다")
    void concurrentEarnDoesNotExceedMaxBalance() {
        earn(100_000, null);
        policyService.updatePolicy(new PointPolicyValues(1, 100_000, 500_000, 365, 1, 1824));

        ConcurrentRun<?> run = runConcurrently(THREAD_COUNT,
                index -> earnService.earn(EarnCommand.ofUser(USER_ID, 100_000, null, null, null)));

        assertThat(run.successCount()).isEqualTo(4);
        assertThat(balanceOf(USER_ID)).isEqualTo(500_000);
    }

    @Test
    @DisplayName("최초 요청이 동시에 들어와도 락 행은 하나만 생성된다")
    void concurrentFirstRequestCreatesSingleLockRow() {
        ConcurrentRun<?> run = runConcurrently(THREAD_COUNT,
                index -> earnService.earn(EarnCommand.ofUser(99L, 10, null, null, null)));

        assertThat(run.successCount()).as("실패 원인: %s", run.failures()).isEqualTo(THREAD_COUNT);
        assertThat(lockRepository.findByUserId(99L)).isPresent();
        assertThat(balanceOf(99L)).isEqualTo(100);
    }
}
