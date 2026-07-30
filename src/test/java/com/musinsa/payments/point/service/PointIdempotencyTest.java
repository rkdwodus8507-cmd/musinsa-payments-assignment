package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.CancelEarnCommand;
import com.musinsa.payments.point.service.dto.CancelUseCommand;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("멱등성 - requestKey 로 중복 요청 차단")
class PointIdempotencyTest extends IntegrationTestSupport {

    private static final String REQUEST_KEY = "req-0001";
    private static final String ORDER_ID = "ORDER-1";

    @Test
    @DisplayName("같은 requestKey 로 적립을 재전송하면 한 번만 적립되고 같은 pointKey 를 돌려준다")
    void duplicatedEarnIsAppliedOnce() {
        EarnResult first = earnWithKey(1000, REQUEST_KEY);
        EarnResult retried = earnWithKey(1000, REQUEST_KEY);

        assertThat(retried.pointKey()).isEqualTo(first.pointKey());
        assertThat(retried.amount()).isEqualTo(1000);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
        assertThat(earnedPointRepository.findByUserIdOrderByIdAsc(USER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("requestKey 가 없으면 중복 차단 없이 각각 적립된다")
    void earnWithoutRequestKeyIsNotDeduplicated() {
        earn(1000, null);
        earn(1000, null);

        assertThat(balanceOf(USER_ID)).isEqualTo(2000);
        assertThat(earnedPointRepository.findByUserIdOrderByIdAsc(USER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("같은 requestKey 로 사용을 재전송하면 한 번만 차감되고 사용 상세도 그대로 재현된다")
    void duplicatedUseIsAppliedOnce() {
        earn(1000, null);

        UseResult first = useWithKey(300, REQUEST_KEY);
        UseResult retried = useWithKey(300, REQUEST_KEY);

        assertThat(retried.pointKey()).isEqualTo(first.pointKey());
        assertThat(retried.details()).isEqualTo(first.details());
        assertThat(balanceOf(USER_ID)).isEqualTo(700);
        assertThat(usageRepository.findByOrderIdOrderByIdAsc(ORDER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("같은 requestKey 로 사용취소를 재전송하면 한 번만 취소된다")
    void duplicatedUseCancelIsAppliedOnce() {
        earn(1000, null);
        UseResult use = use(ORDER_ID, 900);

        UseCancelResult first = useService.cancelUse(new CancelUseCommand(use.pointKey(), 400, REQUEST_KEY));
        UseCancelResult retried = useService.cancelUse(new CancelUseCommand(use.pointKey(), 400, REQUEST_KEY));

        assertThat(retried.pointKey()).isEqualTo(first.pointKey());
        assertThat(retried.remainingCancelableAmount()).isEqualTo(500);
        assertThat(balanceOf(USER_ID)).isEqualTo(500);
    }

    @Test
    @DisplayName("같은 requestKey 로 적립취소를 재전송하면 한 번만 취소된다")
    void duplicatedEarnCancelIsAppliedOnce() {
        EarnResult earn = earn(1000, null);

        String firstKey = earnService.cancelEarn(new CancelEarnCommand(earn.pointKey(), REQUEST_KEY)).pointKey();
        String retriedKey = earnService.cancelEarn(new CancelEarnCommand(earn.pointKey(), REQUEST_KEY)).pointKey();

        assertThat(retriedKey).isEqualTo(firstKey);
        assertThat(balanceOf(USER_ID)).isZero();
    }

    @Test
    @DisplayName("같은 requestKey 를 다른 종류의 요청에 재사용하면 거절된다")
    void requestKeyCannotBeReusedAcrossOperations() {
        earnWithKey(1000, REQUEST_KEY);

        assertThatThrownBy(() -> useWithKey(100, REQUEST_KEY))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(ErrorCode.REQUEST_KEY_CONFLICT);
    }

    @Test
    @DisplayName("사용자가 다르면 같은 requestKey 를 각각 처리한다")
    void requestKeyIsScopedPerUser() {
        earnService.earn(EarnCommand.ofUser(USER_ID, 1000, null, null, REQUEST_KEY));
        earnService.earn(EarnCommand.ofUser(2L, 500, null, null, REQUEST_KEY));

        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
        assertThat(balanceOf(2L)).isEqualTo(500);
    }

    @Test
    @DisplayName("같은 requestKey 로 동시에 10건이 들어와도 차감은 한 번만 일어난다")
    void concurrentDuplicatedUseDeductsOnce() {
        earn(500, null);

        ConcurrentRun<UseResult> run = runConcurrently(10, index -> useWithKey(100, REQUEST_KEY));

        assertThat(run.successCount()).as("실패 원인: %s", run.failures()).isEqualTo(10);
        assertThat(run.successes()).extracting(UseResult::pointKey).containsOnly(run.successes().get(0).pointKey());
        assertThat(balanceOf(USER_ID)).isEqualTo(400);
        assertThat(usageRepository.findByOrderIdOrderByIdAsc(ORDER_ID)).hasSize(1);
    }

    private EarnResult earnWithKey(long amount, String requestKey) {
        return earnService.earn(EarnCommand.ofUser(USER_ID, amount, null, null, requestKey));
    }

    private UseResult useWithKey(long amount, String requestKey) {
        return useService.use(new UseCommand(USER_ID, ORDER_ID, amount, requestKey));
    }
}
