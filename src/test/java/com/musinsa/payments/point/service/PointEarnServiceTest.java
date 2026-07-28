package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.service.dto.EarnCancelResult;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.LotBalance;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import com.musinsa.payments.point.support.MutableClock;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("포인트 적립 / 적립취소")
class PointEarnServiceTest extends IntegrationTestSupport {

    @Test
    @DisplayName("만료일을 지정하지 않으면 정책 기본값 365일이 적용된다")
    void defaultExpireDays() {
        EarnResult result = earn(1000, null);

        assertThat(result.expireAt()).isEqualTo(MutableClock.INITIAL_TIME.plusDays(365));
        assertThat(result.manual()).isFalse();
    }

    @ParameterizedTest(name = "{0}포인트 적립은 허용된다")
    @ValueSource(longs = {1, 50_000, 100_000})
    @DisplayName("1회 적립 가능 금액 경계값")
    void earnWithinPolicyRange(long amount) {
        assertThat(earn(amount, null).amount()).isEqualTo(amount);
    }

    @ParameterizedTest(name = "{0}포인트 적립은 거절된다")
    @ValueSource(longs = {0, -1, 100_001})
    @DisplayName("1회 적립 가능 금액을 벗어나면 거절된다")
    void earnOutsidePolicyRange(long amount) {
        assertErrorCode(() -> earn(amount, null), ErrorCode.INVALID_EARN_AMOUNT);
    }

    @ParameterizedTest(name = "만료일 {0}일은 거절된다")
    @ValueSource(ints = {0, -1, 1825, 3650})
    @DisplayName("만료일은 1일 이상 5년 미만이어야 한다")
    void earnWithInvalidExpireDays(int expireDays) {
        assertErrorCode(() -> earn(1000, expireDays), ErrorCode.INVALID_EXPIRE_DAYS);
    }

    @ParameterizedTest(name = "만료일 {0}일은 허용된다")
    @ValueSource(ints = {1, 365, 1824})
    @DisplayName("만료일 경계값")
    void earnWithValidExpireDays(int expireDays) {
        assertThat(earn(1000, expireDays).expireAt())
                .isEqualTo(MutableClock.INITIAL_TIME.plusDays(expireDays));
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

        assertThat(balance.manualBalance()).isEqualTo(1000);
        assertThat(balance.balance()).isEqualTo(2000);
        assertThat(balance.lots())
                .filteredOn(LotBalance::manual)
                .extracting(LotBalance::earnPointKey)
                .containsExactly(manual.pointKey());
        assertThat(balance.lots())
                .filteredOn(lot -> !lot.manual())
                .extracting(LotBalance::earnPointKey)
                .containsExactly(normal.pointKey());
    }

    @Test
    @DisplayName("사용되지 않은 적립은 전액 취소된다")
    void cancelUnusedEarn() {
        EarnResult earn = earn(1000, null);

        EarnCancelResult result = earnService.cancelEarn(earn.pointKey());

        assertThat(result.amount()).isEqualTo(1000);
        assertThat(result.balance()).isZero();
        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(lotStatusOf(earn.pointKey())).isEqualTo(PointLotStatus.CANCELED);
    }

    @Test
    @DisplayName("일부가 사용된 적립은 취소할 수 없다")
    void cannotCancelPartiallyUsedEarn() {
        EarnResult earn = earn(1000, null);
        use("ORDER-1", 100);

        assertErrorCode(() -> earnService.cancelEarn(earn.pointKey()), ErrorCode.EARN_PARTIALLY_USED);
    }

    @Test
    @DisplayName("이미 취소된 적립은 다시 취소할 수 없다")
    void cannotCancelTwice() {
        EarnResult earn = earn(1000, null);
        earnService.cancelEarn(earn.pointKey());

        assertErrorCode(() -> earnService.cancelEarn(earn.pointKey()), ErrorCode.EARN_ALREADY_CANCELED);
    }

    @Test
    @DisplayName("만료된 적립은 취소할 수 없다")
    void cannotCancelExpiredEarn() {
        EarnResult earn = earn(1000, 1);
        clock.plusDays(2);

        assertErrorCode(() -> earnService.cancelEarn(earn.pointKey()), ErrorCode.EARN_ALREADY_EXPIRED);
    }

    @Test
    @DisplayName("사용 거래는 적립취소 대상이 아니다")
    void cannotCancelEarnWithUseTransaction() {
        earn(1000, null);
        UseResult use = use("ORDER-1", 100);

        assertErrorCode(() -> earnService.cancelEarn(use.pointKey()), ErrorCode.NOT_EARN_TRANSACTION);
    }

    @Test
    @DisplayName("존재하지 않는 pointKey는 조회되지 않는다")
    void cancelEarnWithUnknownPointKey() {
        assertErrorCode(() -> earnService.cancelEarn("unknown-key"), ErrorCode.TRANSACTION_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자별로 잔액이 분리된다")
    void balanceIsolatedPerUser() {
        earn(1000, null);
        earnService.earn(EarnCommand.ofUser(2L, 500, null, null));

        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
        assertThat(balanceOf(2L)).isEqualTo(500);
    }

    private PointLotStatus lotStatusOf(String earnPointKey) {
        return lotRepository.findByTransactionId(
                        transactionRepository.findByPointKey(earnPointKey).orElseThrow().getId())
                .orElseThrow()
                .getStatus();
    }

    private void assertErrorCode(Runnable runnable, ErrorCode expected) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
