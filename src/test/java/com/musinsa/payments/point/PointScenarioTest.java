package com.musinsa.payments.point;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.service.dto.PointResults;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("과제 명세 예시 시나리오")
class PointScenarioTest extends IntegrationTestSupport {

    private static final String ORDER_ID = "A1234";

    @Test
    @DisplayName("적립 A/B → 1200 사용 C → A 만료 → 1100 부분 사용취소 D + 만료분 재적립 E")
    void fullScenario() {
        PointResults.Earn a = earn(1000, 30);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);

        PointResults.Earn b = earn(500, 365);
        assertThat(balanceOf(USER_ID)).isEqualTo(1500);

        PointResults.Use c = use(ORDER_ID, 1200);
        assertThat(balanceOf(USER_ID)).isEqualTo(300);
        assertThat(c.details()).extracting(PointResults.UsedLot::earnPointKey, PointResults.UsedLot::amount)
                .containsExactly(
                        tuple(a.pointKey(), 1000L),
                        tuple(b.pointKey(), 200L));
        assertThat(lotOf(a).getRemainingAmount()).isZero();
        assertThat(lotOf(b).getRemainingAmount()).isEqualTo(300);

        clock.plusDays(31);
        expirationService.expireAll(500);
        assertThat(lotOf(a).getStatus()).isEqualTo(PointLotStatus.EXPIRED);
        assertThat(balanceOf(USER_ID)).isEqualTo(300);

        PointResults.UseCancel d = useService.cancelUse(c.pointKey(), 1100);

        assertThat(balanceOf(USER_ID)).isEqualTo(1400);
        assertThat(d.remainingCancelableAmount()).isEqualTo(100);
        assertThat(lotOf(b).getRemainingAmount()).isEqualTo(400);
        assertThat(lotOf(a).getRemainingAmount()).isZero();

        PointResults.CanceledLot reissued = d.details().stream()
                .filter(PointResults.CanceledLot::reissued)
                .findFirst()
                .orElseThrow();
        assertThat(reissued.earnPointKey()).isEqualTo(a.pointKey());
        assertThat(reissued.amount()).isEqualTo(1000);

        PointLot reissuedLot = lotOf(reissued.reissuedPointKey());
        assertThat(reissuedLot.getRemainingAmount()).isEqualTo(1000);
        assertThat(reissuedLot.getStatus()).isEqualTo(PointLotStatus.AVAILABLE);
        assertThat(reissuedLot.getExpireAt()).isEqualTo(clock.currentDateTime().plusDays(365));

        PointResults.CanceledLot restored = d.details().stream()
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
        PointResults.Use c = use(ORDER_ID, 1200);

        useService.cancelUse(c.pointKey(), 1100);

        assertThatThrownBy(() -> useService.cancelUse(c.pointKey(), 101))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);

        PointResults.UseCancel last = useService.cancelUse(c.pointKey(), 100);
        assertThat(last.remainingCancelableAmount()).isZero();
    }

    @Test
    @DisplayName("주문번호로 1원 단위 사용 내역을 추적할 수 있다")
    void trackUsageByOrderId() {
        PointResults.Earn a = earn(1000, 30);
        PointResults.Earn b = earn(500, 365);
        PointResults.Use c = use(ORDER_ID, 1200);

        PointResults.OrderUsage usage = queryService.getOrderUsage(ORDER_ID);

        assertThat(usage.orderId()).isEqualTo(ORDER_ID);
        assertThat(usage.usedAmount()).isEqualTo(1200);
        assertThat(usage.canceledAmount()).isZero();
        assertThat(usage.details()).hasSize(2);
        assertThat(usage.details())
                .extracting(PointResults.OrderUsageDetail::usePointKey,
                        PointResults.OrderUsageDetail::earnPointKey,
                        PointResults.OrderUsageDetail::amount)
                .containsExactly(
                        tuple(c.pointKey(), a.pointKey(), 1000L),
                        tuple(c.pointKey(), b.pointKey(), 200L));
    }

    private PointLot lotOf(PointResults.Earn earn) {
        return lotOf(earn.pointKey());
    }

    private PointLot lotOf(String pointKey) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey).orElseThrow();
        return lotRepository.findByTransactionId(transaction.getId()).orElseThrow();
    }
}
