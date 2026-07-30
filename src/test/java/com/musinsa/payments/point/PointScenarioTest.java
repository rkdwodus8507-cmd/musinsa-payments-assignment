package com.musinsa.payments.point;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.service.dto.CanceledPointDetail;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.OrderUsageDetail;
import com.musinsa.payments.point.service.dto.OrderUsageResult;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.service.dto.UsedPointDetail;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("과제 명세 예시 시나리오")
class PointScenarioTest extends IntegrationTestSupport {

    private static final String ORDER_ID = "A1234";

    @Test
    @DisplayName("적립 A/B → 1200 사용 C → A 만료 → 1100 부분 사용취소 D + 만료분 재적립 E")
    void fullScenario() {
        EarnResult a = earn(1000, 30);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);

        EarnResult b = earn(500, 365);
        assertThat(balanceOf(USER_ID)).isEqualTo(1500);

        UseResult c = use(ORDER_ID, 1200);
        assertThat(balanceOf(USER_ID)).isEqualTo(300);
        assertThat(c.getDetails()).extracting(UsedPointDetail::getEarnPointKey, UsedPointDetail::getAmount)
                .containsExactly(
                        tuple(a.getPointKey(), 1000L),
                        tuple(b.getPointKey(), 200L));
        assertThat(earnedPointOf(a).getRemainingAmount()).isZero();
        assertThat(earnedPointOf(b).getRemainingAmount()).isEqualTo(300);

        clock.plusDays(31);
        expirationService.expireAll();
        assertThat(earnedPointOf(a).getStatus()).isEqualTo(EarnedPointStatus.EXPIRED);
        assertThat(balanceOf(USER_ID)).isEqualTo(300);

        UseCancelResult d = cancelUse(c.getPointKey(), 1100);

        assertThat(balanceOf(USER_ID)).isEqualTo(1400);
        assertThat(d.getRemainingCancelableAmount()).isEqualTo(100);
        assertThat(earnedPointOf(b).getRemainingAmount()).isEqualTo(400);
        assertThat(earnedPointOf(a).getRemainingAmount()).isZero();

        CanceledPointDetail reissued = d.getDetails().stream()
                .filter(CanceledPointDetail::isReissued)
                .findFirst()
                .orElseThrow();
        assertThat(reissued.getEarnPointKey()).isEqualTo(a.getPointKey());
        assertThat(reissued.getAmount()).isEqualTo(1000);

        EarnedPoint reissuedEarnedPoint = earnedPointOf(reissued.getReissuedPointKey());
        assertThat(reissuedEarnedPoint.getRemainingAmount()).isEqualTo(1000);
        assertThat(reissuedEarnedPoint.getStatus()).isEqualTo(EarnedPointStatus.AVAILABLE);
        assertThat(reissuedEarnedPoint.getExpireAt()).isEqualTo(clock.currentDateTime().plusDays(365));

        CanceledPointDetail restored = d.getDetails().stream()
                .filter(detail -> !detail.isReissued())
                .findFirst()
                .orElseThrow();
        assertThat(restored.getEarnPointKey()).isEqualTo(b.getPointKey());
        assertThat(restored.getAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("잔여 취소가능 금액을 초과하면 사용취소가 거절된다")
    void cancelUseBeyondRemainingAmount() {
        earn(1000, 30);
        earn(500, 365);
        UseResult c = use(ORDER_ID, 1200);

        cancelUse(c.getPointKey(), 1100);

        assertThatThrownBy(() -> cancelUse(c.getPointKey(), 101))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);

        UseCancelResult last = cancelUse(c.getPointKey(), 100);
        assertThat(last.getRemainingCancelableAmount()).isZero();
    }

    @Test
    @DisplayName("주문번호로 1원 단위 사용 내역을 추적할 수 있다")
    void trackUsageByOrderId() {
        EarnResult a = earn(1000, 30);
        EarnResult b = earn(500, 365);
        UseResult c = use(ORDER_ID, 1200);

        OrderUsageResult usage = queryService.getOrderUsage(ORDER_ID);

        assertThat(usage.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(usage.getUsedAmount()).isEqualTo(1200);
        assertThat(usage.getCanceledAmount()).isZero();
        assertThat(usage.getDetails()).hasSize(2);
        assertThat(usage.getDetails())
                .extracting(OrderUsageDetail::getUsePointKey,
                        OrderUsageDetail::getEarnPointKey,
                        OrderUsageDetail::getAmount)
                .containsExactly(
                        tuple(c.getPointKey(), a.getPointKey(), 1000L),
                        tuple(c.getPointKey(), b.getPointKey(), 200L));
    }

    private EarnedPoint earnedPointOf(EarnResult earn) {
        return earnedPointOf(earn.getPointKey());
    }

    private EarnedPoint earnedPointOf(String pointKey) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey).orElseThrow();
        return earnedPointRepository.findByTransactionId(transaction.getId()).orElseThrow();
    }
}
