package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.musinsa.payments.point.support.PointAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용 상세 - 취소 가능 금액 규칙")
class PointUsageTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    @DisplayName("취소 가능 금액은 사용액에서 기취소액을 뺀 값이다")
    void cancelableAmount() {
        PointUsage usage = usage(1000);
        assertThat(usage.cancelableAmount()).isEqualTo(1000);

        usage.cancel(300);
        assertThat(usage.getCanceledAmount()).isEqualTo(300);
        assertThat(usage.cancelableAmount()).isEqualTo(700);
    }

    @Test
    @DisplayName("부분 취소를 여러 번 누적할 수 있다")
    void cancelRepeatedly() {
        PointUsage usage = usage(1000);

        usage.cancel(400);
        usage.cancel(600);

        assertThat(usage.cancelableAmount()).isZero();
    }

    @Test
    @DisplayName("취소 가능 금액을 넘기면 거절된다")
    void cancelBeyondCancelable() {
        PointUsage usage = usage(1000);
        usage.cancel(900);

        assertErrorCode(() -> usage.cancel(101), ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);
        assertErrorCode(() -> usage.cancel(0), ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);
    }

    private PointUsage usage(long amount) {
        PointTransaction useTransaction = PointTransaction.use(1L, amount, "ORDER-1", null, NOW);
        EarnedPoint source = EarnedPoint.from(
                PointTransaction.earn(1L, amount, null, null, NOW), false, NOW.plusDays(30), NOW);
        return PointUsage.of(useTransaction, source, amount, NOW);
    }

}
