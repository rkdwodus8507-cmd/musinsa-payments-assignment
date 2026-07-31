package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.musinsa.payments.point.support.PointAssertions.assertErrorCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("포인트 정책 - 금액 / 만료일 / 보유한도 규칙")
class PointPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    private final PointPolicy policy = PointPolicy.create(
            new PointPolicyValues(1, 100_000, 1_000_000, 365, 1, 1824), NOW);

    @ParameterizedTest(name = "{0}포인트 적립은 허용된다")
    @ValueSource(longs = {1, 50_000, 100_000})
    @DisplayName("1회 적립 가능 금액 경계값")
    void earnAmountWithinRange(long amount) {
        assertThatCode(() -> policy.validateEarnAmount(amount)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}포인트 적립은 거절된다")
    @ValueSource(longs = {0, -1, 100_001})
    @DisplayName("1회 적립 가능 금액을 벗어나면 거절된다")
    void earnAmountOutOfRange(long amount) {
        assertErrorCode(() -> policy.validateEarnAmount(amount), ErrorCode.INVALID_EARN_AMOUNT);
    }

    @Test
    @DisplayName("만료일을 지정하지 않으면 기본값이 쓰인다")
    void resolveDefaultExpireDays() {
        assertThat(policy.resolveExpireDays(null)).isEqualTo(365);
    }

    @ParameterizedTest(name = "만료일 {0}일은 허용된다")
    @ValueSource(ints = {1, 365, 1824})
    @DisplayName("만료일 경계값")
    void resolveExpireDaysWithinRange(int expireDays) {
        assertThat(policy.resolveExpireDays(expireDays)).isEqualTo(expireDays);
    }

    @ParameterizedTest(name = "만료일 {0}일은 거절된다")
    @ValueSource(ints = {0, -1, 1825, 3650})
    @DisplayName("만료일은 1일 이상 5년 미만이어야 한다")
    void resolveExpireDaysOutOfRange(int expireDays) {
        assertErrorCode(() -> policy.resolveExpireDays(expireDays), ErrorCode.INVALID_EXPIRE_DAYS);
    }

    @Test
    @DisplayName("적립 후 잔액이 최대 보유금액을 넘으면 거절된다")
    void validateBalanceAfterEarn() {
        assertThatCode(() -> policy.validateBalanceAfterEarn(900_000, 100_000)).doesNotThrowAnyException();
        assertErrorCode(() -> policy.validateBalanceAfterEarn(1_000_000, 1), ErrorCode.MAX_BALANCE_EXCEEDED);
    }

    @Test
    @DisplayName("변경한 정책값이 즉시 반영된다")
    void update() {
        policy.update(new PointPolicyValues(1, 200_000, 1_000_000, 30, 1, 1824), NOW.plusDays(1));

        assertThatCode(() -> policy.validateEarnAmount(200_000)).doesNotThrowAnyException();
        assertThat(policy.resolveExpireDays(null)).isEqualTo(30);
        assertThat(policy.getUpdatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("최대 만료일은 5년(1825일) 미만까지만 설정할 수 있다")
    void maxExpireDaysCannotReachFiveYears() {
        assertInvalidPolicy(new PointPolicyValues(1, 100_000, 1_000_000, 365, 1, 1825));
    }

    @Test
    @DisplayName("최대 보유금액은 1회 최대 적립금액보다 작을 수 없다")
    void maxUserBalanceMustCoverMaxEarnAmount() {
        assertInvalidPolicy(new PointPolicyValues(1, 100_000, 50_000, 365, 1, 1824));
    }

    @Test
    @DisplayName("적립 금액 범위가 뒤집히면 거절된다")
    void earnAmountRangeMustBeOrdered() {
        assertInvalidPolicy(new PointPolicyValues(100_000, 1, 1_000_000, 365, 1, 1824));
        assertInvalidPolicy(new PointPolicyValues(0, 100_000, 1_000_000, 365, 1, 1824));
    }

    @Test
    @DisplayName("기본 만료일이 허용 범위를 벗어나면 거절된다")
    void defaultExpireDaysMustBeWithinRange() {
        assertInvalidPolicy(new PointPolicyValues(1, 100_000, 1_000_000, 400, 1, 300));
    }

    private void assertInvalidPolicy(PointPolicyValues values) {
        assertErrorCode(() -> PointPolicy.create(values, NOW), ErrorCode.INVALID_POLICY);
    }

}
