package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.service.dto.CanceledLot;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.service.dto.UsedLot;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("포인트 사용 / 사용취소")
class PointUseServiceTest extends IntegrationTestSupport {

    private static final String ORDER_ID = "ORDER-1";

    @Test
    @DisplayName("수기지급 포인트가 만료일과 무관하게 먼저 사용된다")
    void manualPointIsUsedFirst() {
        EarnResult normalSoon = earn(1000, 10);
        EarnResult manualLater = manualEarn(500, 300);

        UseResult use = use(ORDER_ID, 700);

        assertThat(use.details())
                .extracting(UsedLot::earnPointKey, UsedLot::amount)
                .containsExactly(
                        tuple(manualLater.pointKey(), 500L),
                        tuple(normalSoon.pointKey(), 200L));
    }

    @Test
    @DisplayName("수기지급끼리는 만료일이 짧게 남은 순서로 사용된다")
    void manualPointsFollowExpiryOrder() {
        EarnResult later = manualEarn(300, 100);
        EarnResult sooner = manualEarn(300, 10);

        UseResult use = use(ORDER_ID, 400);

        assertThat(use.details())
                .extracting(UsedLot::earnPointKey, UsedLot::amount)
                .containsExactly(
                        tuple(sooner.pointKey(), 300L),
                        tuple(later.pointKey(), 100L));
    }

    @Test
    @DisplayName("일반 적립은 만료일이 짧게 남은 순서로 사용된다")
    void normalPointsFollowExpiryOrder() {
        EarnResult later = earn(300, 200);
        EarnResult sooner = earn(300, 20);
        EarnResult middle = earn(300, 50);

        UseResult use = use(ORDER_ID, 750);

        assertThat(use.details())
                .extracting(UsedLot::earnPointKey, UsedLot::amount)
                .containsExactly(
                        tuple(sooner.pointKey(), 300L),
                        tuple(middle.pointKey(), 300L),
                        tuple(later.pointKey(), 150L));
    }

    @Test
    @DisplayName("만료된 포인트는 사용되지 않는다")
    void expiredPointIsNotUsable() {
        earn(1000, 1);
        earn(500, 365);
        clock.plusDays(2);

        assertThat(balanceOf(USER_ID)).isEqualTo(500);
        assertErrorCode(() -> use(ORDER_ID, 600), ErrorCode.INSUFFICIENT_BALANCE);

        UseResult use = use(ORDER_ID, 500);
        assertThat(use.details()).hasSize(1);
    }

    @Test
    @DisplayName("잔액보다 많이 사용할 수 없다")
    void cannotUseBeyondBalance() {
        earn(1000, null);

        assertErrorCode(() -> use(ORDER_ID, 1001), ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
    }

    @Test
    @DisplayName("전액 사용취소하면 원 적립분이 그대로 복원된다")
    void fullCancelRestoresOriginalLots() {
        EarnResult a = earn(1000, 10);
        EarnResult b = earn(500, 365);
        UseResult use = use(ORDER_ID, 1200);

        UseCancelResult cancel = useService.cancelUse(use.pointKey(), 1200);

        assertThat(cancel.balance()).isEqualTo(1500);
        assertThat(cancel.remainingCancelableAmount()).isZero();
        assertThat(cancel.details())
                .extracting(CanceledLot::earnPointKey,
                        CanceledLot::amount,
                        CanceledLot::reissued)
                .containsExactly(
                        tuple(a.pointKey(), 1000L, false),
                        tuple(b.pointKey(), 200L, false));
    }

    @Test
    @DisplayName("사용취소를 여러 번 나누어 할 수 있다")
    void partialCancelRepeatedly() {
        earn(1000, null);
        UseResult use = use(ORDER_ID, 900);

        assertThat(useService.cancelUse(use.pointKey(), 300).remainingCancelableAmount()).isEqualTo(600);
        assertThat(useService.cancelUse(use.pointKey(), 300).remainingCancelableAmount()).isEqualTo(300);
        assertThat(useService.cancelUse(use.pointKey(), 300).remainingCancelableAmount()).isZero();
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);

        assertErrorCode(() -> useService.cancelUse(use.pointKey(), 1), ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("사용취소로 재적립된 포인트는 수기지급 여부를 승계한다")
    void reissuedPointInheritsManualFlag() {
        manualEarn(1000, 5);
        UseResult use = use(ORDER_ID, 1000);
        clock.plusDays(6);
        expirationService.expireAll(100);

        useService.cancelUse(use.pointKey(), 1000);

        BalanceResult balance = queryService.getBalance(USER_ID);
        assertThat(balance.balance()).isEqualTo(1000);
        assertThat(balance.manualBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("만료 배치가 돌지 않았어도 만료 시각이 지난 적립분은 재적립으로 처리된다")
    void reissueWithoutExpirationBatch() {
        earn(1000, 5);
        UseResult use = use(ORDER_ID, 1000);
        clock.plusDays(6);

        UseCancelResult cancel = useService.cancelUse(use.pointKey(), 1000);

        assertThat(cancel.details()).singleElement()
                .extracting(CanceledLot::reissued)
                .isEqualTo(true);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
    }

    @Test
    @DisplayName("사용취소로 인한 재적립은 최대 보유 한도를 초과해도 허용된다")
    void reissueIgnoresMaxBalance() {
        earn(100_000, 5);
        UseResult use = use(ORDER_ID, 100_000);
        for (int i = 0; i < 10; i++) {
            earn(100_000, 365);
        }
        assertThat(balanceOf(USER_ID)).isEqualTo(1_000_000);

        clock.plusDays(6);
        useService.cancelUse(use.pointKey(), 100_000);

        assertThat(balanceOf(USER_ID)).isEqualTo(1_100_000);
    }

    @Test
    @DisplayName("적립 거래는 사용취소 대상이 아니다")
    void cannotCancelUseWithEarnTransaction() {
        EarnResult earn = earn(1000, null);

        assertErrorCode(() -> useService.cancelUse(earn.pointKey(), 100), ErrorCode.NOT_USE_TRANSACTION);
    }

    private void assertErrorCode(Runnable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
