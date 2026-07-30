package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.service.dto.EarnCancelResult;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.EarnedPointSummary;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.MutableClock;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("포인트 적립 / 적립취소")
class PointEarnServiceTest extends IntegrationTestSupport {

    @Test
    @DisplayName("만료일을 지정하지 않으면 정책 기본값 365일이 적용된다")
    void defaultExpireDays() {
        EarnResult result = earn(1000, null);

        assertThat(result.getExpireAt()).isEqualTo(MutableClock.INITIAL_TIME.plusDays(365));
        assertThat(result.isManual()).isFalse();
    }





    @Test
    @DisplayName("개인별 최대 보유 포인트를 초과하면 적립이 거절된다")
    void earnBeyondMaxUserBalance() {
        for (int i = 0; i < 10; i++) {
            earn(100_000, null);
        }
        assertThat(balanceOf(USER_ID)).isEqualTo(1_000_000);

        assertErrorCode(() -> earn(1, null), ErrorCode.MAX_BALANCE_EXCEEDED);
    }

    @Test
    @DisplayName("수기지급 포인트는 일반 적립과 구분되어 식별된다")
    void manualEarnIsDistinguishable() {
        EarnResult manual = manualEarn(1000, null);
        EarnResult normal = earn(1000, null);

        BalanceResult balance = queryService.getBalance(USER_ID);

        assertThat(balance.getManualBalance()).isEqualTo(1000);
        assertThat(balance.getBalance()).isEqualTo(2000);
        assertThat(balance.getEarnedPoints())
                .filteredOn(EarnedPointSummary::isManual)
                .extracting(EarnedPointSummary::getEarnPointKey)
                .containsExactly(manual.getPointKey());
        assertThat(balance.getEarnedPoints())
                .filteredOn(lot -> !lot.isManual())
                .extracting(EarnedPointSummary::getEarnPointKey)
                .containsExactly(normal.getPointKey());
    }

    @Test
    @DisplayName("사용되지 않은 적립은 전액 취소된다")
    void cancelUnusedEarn() {
        EarnResult earn = earn(1000, null);

        EarnCancelResult result = cancelEarn(earn.getPointKey());

        assertThat(result.getAmount()).isEqualTo(1000);
        assertThat(result.getBalance()).isZero();
        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(lotStatusOf(earn.getPointKey())).isEqualTo(EarnedPointStatus.CANCELED);
    }

    @Test
    @DisplayName("일부가 사용된 적립은 취소할 수 없다")
    void cannotCancelPartiallyUsedEarn() {
        EarnResult earn = earn(1000, null);
        use("ORDER-1", 100);

        assertErrorCode(() -> cancelEarn(earn.getPointKey()), ErrorCode.EARN_PARTIALLY_USED);
    }

    @Test
    @DisplayName("이미 취소된 적립은 다시 취소할 수 없다")
    void cannotCancelTwice() {
        EarnResult earn = earn(1000, null);
        cancelEarn(earn.getPointKey());

        assertErrorCode(() -> cancelEarn(earn.getPointKey()), ErrorCode.EARN_ALREADY_CANCELED);
    }

    @Test
    @DisplayName("만료된 적립은 취소할 수 없다")
    void cannotCancelExpiredEarn() {
        EarnResult earn = earn(1000, 1);
        clock.plusDays(2);

        assertErrorCode(() -> cancelEarn(earn.getPointKey()), ErrorCode.EARN_ALREADY_EXPIRED);
    }

    @Test
    @DisplayName("사용 거래는 적립취소 대상이 아니다")
    void cannotCancelEarnWithUseTransaction() {
        earn(1000, null);
        UseResult use = use("ORDER-1", 100);

        assertErrorCode(() -> cancelEarn(use.getPointKey()), ErrorCode.NOT_EARN_TRANSACTION);
    }

    @Test
    @DisplayName("존재하지 않는 pointKey는 조회되지 않는다")
    void cancelEarnWithUnknownPointKey() {
        assertErrorCode(() -> cancelEarn("unknown-key"), ErrorCode.TRANSACTION_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자별로 잔액이 분리된다")
    void balanceIsolatedPerUser() {
        earn(1000, null);
        earnService.earn(EarnCommand.ofUser(2L, 500, null, null, null));

        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
        assertThat(balanceOf(2L)).isEqualTo(500);
    }

    private EarnedPointStatus lotStatusOf(String earnPointKey) {
        return earnedPointRepository.findByTransactionId(
                        transactionRepository.findByPointKey(earnPointKey).orElseThrow().getId())
                .orElseThrow()
                .getStatus();
    }

}
