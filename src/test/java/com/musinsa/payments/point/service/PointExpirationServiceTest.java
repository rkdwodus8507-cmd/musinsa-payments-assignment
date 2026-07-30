package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("만료 배치 - 사용자 단위 락 안에서 전이한다")
class PointExpirationServiceTest extends IntegrationTestSupport {

    @Test
    @DisplayName("만료 전이는 사용자 락을 잡고 수행한다")
    void expirationAcquiresUserLock() {
        earn(1000, 1);
        clock.plusDays(2);
        lockRepository.deleteAllInBatch();
        assertThat(lockRepository.findByUserId(USER_ID)).isEmpty();

        expirationService.expireAll();

        assertThat(lockRepository.findByUserId(USER_ID))
                .as("배치가 락을 잡지 않으면 사용 트랜잭션과 경합해 만료된 적립분에서 차감될 수 있다")
                .isPresent();
    }

    @Test
    @DisplayName("사용자가 여러 명이어도 각각 만료되고 배치는 종료된다")
    void expiresEveryOwnerAndTerminates() {
        for (long userId = 1; userId <= 5; userId++) {
            earnService.earn(EarnCommand.ofUser(userId, 1000, 1, null, null));
        }
        clock.plusDays(2);

        ExpirationResult result = expirationService.expireAll();

        assertThat(result.getExpiredCount()).isEqualTo(5);
        assertThat(result.getExpiredAmount()).isEqualTo(5000);
        for (long userId = 1; userId <= 5; userId++) {
            assertThat(earnedPointRepository.findByUserIdOrderByIdAsc(userId))
                    .allSatisfy(it -> assertThat(it.getStatus()).isEqualTo(EarnedPointStatus.EXPIRED));
        }
    }

    @Test
    @DisplayName("만료 대상이 없으면 아무것도 하지 않는다")
    void nothingToExpire() {
        earn(1000, 365);

        ExpirationResult result = expirationService.expireAll();

        assertThat(result.getExpiredCount()).isZero();
        assertThat(result.getExpiredAmount()).isZero();
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
    }

    @Test
    @DisplayName("이미 사용된 적립분은 남은 금액만 만료 금액으로 집계된다")
    void expiresOnlyRemainingAmount() {
        earn(1000, 1);
        use("ORDER-1", 400);
        clock.plusDays(2);

        ExpirationResult result = expirationService.expireAll();

        assertThat(result.getExpiredAmount()).isEqualTo(600);
    }
}
