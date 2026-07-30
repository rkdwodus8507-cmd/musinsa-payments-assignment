package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.musinsa.payments.point.support.PointAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("적립분 - 차감 / 복원 / 취소 / 만료 규칙")
class EarnedPointTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    @DisplayName("차감하면 잔액이 줄어든다")
    void deduct() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));

        earnedPoint.deduct(300);

        assertThat(earnedPoint.getRemainingAmount()).isEqualTo(700);
        assertThat(earnedPoint.getOriginalAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("잔액보다 많이 차감할 수 없다")
    void deductBeyondRemaining() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));

        assertErrorCode(() -> earnedPoint.deduct(1001), ErrorCode.INTERNAL_ERROR);
        assertErrorCode(() -> earnedPoint.deduct(0), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("복원은 원 적립금액을 넘을 수 없다")
    void restore() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));
        earnedPoint.deduct(400);

        earnedPoint.restore(400);

        assertThat(earnedPoint.getRemainingAmount()).isEqualTo(1000);
        assertErrorCode(() -> earnedPoint.restore(1), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("사용되지 않은 적립분은 취소된다")
    void cancel() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));

        earnedPoint.cancel(NOW);

        assertThat(earnedPoint.getStatus()).isEqualTo(EarnedPointStatus.CANCELED);
        assertThat(earnedPoint.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("이미 취소된 적립분은 다시 취소할 수 없다")
    void cancelTwice() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));
        earnedPoint.cancel(NOW);

        assertErrorCode(() -> earnedPoint.cancel(NOW), ErrorCode.EARN_ALREADY_CANCELED);
    }

    @Test
    @DisplayName("만료된 적립분은 취소할 수 없다")
    void cancelExpired() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(1));

        assertErrorCode(() -> earnedPoint.cancel(NOW.plusDays(2)), ErrorCode.EARN_ALREADY_EXPIRED);
    }

    @Test
    @DisplayName("일부라도 사용된 적립분은 취소할 수 없다")
    void cancelPartiallyUsed() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));
        earnedPoint.deduct(1);

        assertErrorCode(() -> earnedPoint.cancel(NOW), ErrorCode.EARN_PARTIALLY_USED);
    }

    @Test
    @DisplayName("만료 전이는 AVAILABLE 인 적립분만 바꾼다")
    void expire() {
        EarnedPoint available = earnedPoint(1000, false, NOW.plusDays(30));
        available.expire();
        assertThat(available.getStatus()).isEqualTo(EarnedPointStatus.EXPIRED);

        EarnedPoint canceled = earnedPoint(1000, false, NOW.plusDays(30));
        canceled.cancel(NOW);
        canceled.expire();
        assertThat(canceled.getStatus()).isEqualTo(EarnedPointStatus.CANCELED);
    }

    @Test
    @DisplayName("잔액이 없거나 만료됐으면 사용할 수 없다")
    void canBeUsedAt() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));
        assertThat(earnedPoint.canBeUsedAt(NOW)).isTrue();
        assertThat(earnedPoint.canBeUsedAt(NOW.plusDays(31))).isFalse();

        earnedPoint.deduct(1000);
        assertThat(earnedPoint.canBeUsedAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("잔액이 0이어도 만료 전이면 복원할 수 있다")
    void canBeRestoredAt() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));
        earnedPoint.deduct(1000);

        assertThat(earnedPoint.canBeRestoredAt(NOW)).isTrue();
        assertThat(earnedPoint.canBeRestoredAt(NOW.plusDays(31))).isFalse();
    }

    @Test
    @DisplayName("만료 시각이 지났거나 상태가 EXPIRED 면 만료로 본다")
    void isExpiredAt() {
        EarnedPoint earnedPoint = earnedPoint(1000, false, NOW.plusDays(30));

        assertThat(earnedPoint.isExpiredAt(NOW)).isFalse();
        assertThat(earnedPoint.isExpiredAt(NOW.plusDays(30))).isTrue();

        earnedPoint.expire();
        assertThat(earnedPoint.isExpiredAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("수기지급 여부는 적립분에 그대로 담긴다")
    void manualFlag() {
        assertThat(earnedPoint(1000, true, NOW.plusDays(30)).isManual()).isTrue();
        assertThat(earnedPoint(1000, false, NOW.plusDays(30)).isManual()).isFalse();
    }

    private EarnedPoint earnedPoint(long amount, boolean manual, LocalDateTime expireAt) {
        PointTransaction earnTransaction = PointTransaction.earn(1L, amount, null, null, NOW);
        return EarnedPoint.from(earnTransaction, manual, expireAt, NOW);
    }

}
