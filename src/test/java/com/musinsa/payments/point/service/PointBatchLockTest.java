package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.musinsa.payments.point.service.PointExpirationService.EXPIRATION_LOCK;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("만료 배치 - 인스턴스 하나만 돈다")
class PointBatchLockTest extends IntegrationTestSupport {

    @Autowired
    private com.musinsa.payments.point.support.batch.BatchLockStore lockStore;

    @Test
    @DisplayName("다른 인스턴스가 락을 잡고 있으면 배치를 건너뛴다")
    void skipsWhenAnotherInstanceHoldsLock() {
        earn(1000, 1);
        clock.plusDays(2);
        assertThat(lockStore.tryAcquire(EXPIRATION_LOCK, "other-instance", Duration.ofMinutes(10))).isTrue();

        ExpirationResult result = expirationService.expireAll();

        assertThat(result.getExpiredCount()).isZero();
        assertThat(earnedPointRepository.findByUserIdOrderByIdAsc(USER_ID))
                .allSatisfy(it -> assertThat(it.getStatus()).isEqualTo(EarnedPointStatus.AVAILABLE));
    }

    @Test
    @DisplayName("락이 풀려 있으면 배치가 정상 수행되고 끝나면 다시 풀린다")
    void runsAndReleasesLock() {
        earn(1000, 1);
        clock.plusDays(2);

        assertThat(expirationService.expireAll().getExpiredCount()).isEqualTo(1);
        assertThat(lockStore.tryAcquire(EXPIRATION_LOCK, "other-instance", Duration.ofMinutes(10)))
                .as("배치가 끝나면 락을 반납해야 다음 실행이 가능하다")
                .isTrue();
    }

    @Test
    @DisplayName("TTL 이 지난 락은 다른 인스턴스가 회수한다")
    void expiredLockIsReclaimed() {
        assertThat(lockStore.tryAcquire(EXPIRATION_LOCK, "crashed-instance", Duration.ofMinutes(10))).isTrue();
        assertThat(lockStore.tryAcquire(EXPIRATION_LOCK, "next-instance", Duration.ofMinutes(10)))
                .as("아직 TTL 안이라 회수되면 안 된다")
                .isFalse();

        clock.plusDays(1);

        assertThat(lockStore.tryAcquire(EXPIRATION_LOCK, "next-instance", Duration.ofMinutes(10)))
                .as("죽은 인스턴스가 락을 영원히 쥐고 있으면 만료가 멈춘다")
                .isTrue();
    }
}
