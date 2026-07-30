package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("잔액 조회 - 적립분이 많아도 전체를 읽지 않는다")
class PointBalanceQueryTest extends IntegrationTestSupport {

    @Test
    @DisplayName("적립분이 상한보다 많아도 응답 목록은 상한까지만 담긴다")
    void earnedPointListIsCapped() {
        for (int i = 0; i < 120; i++) {
            earn(10, 365);
        }

        BalanceResult balance = queryService.getBalance(USER_ID);

        assertThat(balance.getBalance()).isEqualTo(1200);
        assertThat(balance.getEarnedPoints())
                .as("무제한으로 담으면 적립분 수만 건 사용자의 잔액 조회가 힙을 태운다")
                .hasSize(100);
    }

    @Test
    @DisplayName("읽는 엔티티 수는 사용자의 전체 적립분 수가 아니라 목록 상한에 묶인다")
    void entityLoadsAreBoundedByListCap() {
        for (int i = 0; i < 120; i++) {
            earn(10, 365);
        }

        Statistics statistics = startCountingQueries();

        BalanceResult balance = queryService.getBalance(USER_ID);

        assertThat(balance.getBalance()).isEqualTo(1200);
        assertThat(statistics.getEntityLoadCount())
                .as("엔티티 %d건을 읽었다 — 적립분 120건을 전부 읽었다면 240건이 나온다", statistics.getEntityLoadCount())
                .isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("잔액과 수기지급 잔액은 집계 쿼리로 구한다")
    void totalsComeFromAggregates() {
        earn(1000, 365);
        manualEarn(500, 365);
        earn(300, 1);
        clock.plusDays(2);

        BalanceResult balance = queryService.getBalance(USER_ID);

        assertThat(balance.getBalance()).isEqualTo(1500);
        assertThat(balance.getManualBalance()).isEqualTo(500);
    }
}
