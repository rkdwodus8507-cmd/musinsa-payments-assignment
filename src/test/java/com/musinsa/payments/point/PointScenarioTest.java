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
        assertThat(c.details()).extracting(UsedPointDetail::earnPointKey, UsedPointDetail::amount)
                .containsExactly(
                        tuple(a.pointKey(), 1000L),
                        tuple(b.pointKey(), 200L));
        assertThat(earnedPointOf(a).getRemainingAmount()).isZero();
        assertThat(earnedPointOf(b).getRemainingAmount()).isEqualTo(300);

        clock.plusDays(31);
        expirationService.expireAll();
        assertThat(earnedPointOf(a).getStatus()).isEqualTo(EarnedPointStatus.EXPIRED);
        assertThat(balanceOf(USER_ID)).isEqualTo(300);

        UseCancelResult d = cancelUse(c.pointKey(), 1100);

        assertThat(balanceOf(USER_ID)).isEqualTo(1400);
        assertThat(d.remainingCancelableAmount()).isEqualTo(100);
        assertThat(earnedPointOf(b).getRemainingAmount()).isEqualTo(400);
        assertThat(earnedPointOf(a).getRemainingAmount()).isZero();

        CanceledPointDetail reissued = d.details().stream()
                .filter(CanceledPointDetail::reissued)
                .findFirst()
                .orElseThrow();
        assertThat(reissued.earnPointKey()).isEqualTo(a.pointKey());
        assertThat(reissued.amount()).isEqualTo(1000);

        EarnedPoint reissuedEarnedPoint = earnedPointOf(reissued.reissuedPointKey());
        assertThat(reissuedEarnedPoint.getRemainingAmount()).isEqualTo(1000);
        assertThat(reissuedEarnedPoint.getStatus()).isEqualTo(EarnedPointStatus.AVAILABLE);
        assertThat(reissuedEarnedPoint.getExpireAt()).isEqualTo(clock.currentDateTime().plusDays(365));

        CanceledPointDetail restored = d.details().stream()
                .filter(detail -> !detail.reissued())
                .findFirst()
                .orElseThrow();
        assertThat(restored.earnPointKey()).isEqualTo(b.pointKey());
        assertThat(restored.amount()).isEqualTo(100);
    }

    @Test
    @DisplayName("잔여 취소가능 금액을 초과하면 사용취소가 거절된다")
    void cancelUseBeyondRemainingAmount() {
        earn(1000, 30);
        earn(500, 365);
        UseResult c = use(ORDER_ID, 1200);

        cancelUse(c.pointKey(), 1100);

        assertThatThrownBy(() -> cancelUse(c.pointKey(), 101))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);

        UseCancelResult last = cancelUse(c.pointKey(), 100);
        assertThat(last.remainingCancelableAmount()).isZero();
    }

    @Test
    @DisplayName("주문번호로 1원 단위 사용 내역을 추적할 수 있다")
    void trackUsageByOrderId() {
        EarnResult a = earn(1000, 30);
        EarnResult b = earn(500, 365);
        UseResult c = use(ORDER_ID, 1200);

        OrderUsageResult usage = queryService.getOrderUsage(ORDER_ID);

        assertThat(usage.orderId()).isEqualTo(ORDER_ID);
        assertThat(usage.usedAmount()).isEqualTo(1200);
        assertThat(usage.canceledAmount()).isZero();
        assertThat(usage.details()).hasSize(2);
        assertThat(usage.details())
                .extracting(OrderUsageDetail::usePointKey,
                        OrderUsageDetail::earnPointKey,
                        OrderUsageDetail::amount)
                .containsExactly(
                        tuple(c.pointKey(), a.pointKey(), 1000L),
                        tuple(c.pointKey(), b.pointKey(), 200L));
    }

    private EarnedPoint earnedPointOf(EarnResult earn) {
        return earnedPointOf(earn.pointKey());
    }

    private EarnedPoint earnedPointOf(String pointKey) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey).orElseThrow();
        return earnedPointRepository.findByTransactionId(transaction.getId()).orElseThrow();
    }
}
