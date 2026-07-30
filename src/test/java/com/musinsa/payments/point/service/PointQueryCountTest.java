package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("쿼리 수 - 적립분이 늘어도 조회는 늘지 않는다")
class PointQueryCountTest extends IntegrationTestSupport {

    private static final String ORDER_ID = "ORDER-1";
    private static final int FEW = 2;
    private static final int MANY = 6;

    @Test
    @DisplayName("사용은 적립분당 쓰기 2건(사용상세 insert + 적립분 update)만 늘어난다")
    void useCostsTwoWritesPerSource() {
        assertThat(statementsPerExtraSource(this::countStatementsForUse))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("사용취소는 적립분당 쓰기 3건(사용상세 update + 적립분 update + 취소상세 insert)만 늘어난다")
    void cancelUseCostsThreeWritesPerSource() {
        assertThat(statementsPerExtraSource(this::countStatementsForCancelUse))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("만료분 재적립 취소는 적립분당 쓰기 4건만 늘어난다 (정책은 매번 다시 읽지 않는다)")
    void reissueCostsFourWritesPerSource() {
        assertThat(statementsPerExtraSource(this::countStatementsForExpiredCancelUse))
                .isEqualTo(4);
    }

    private long statementsPerExtraSource(java.util.function.IntToLongFunction measure) {
        long few = measure.applyAsLong(FEW);
        resetState();
        long many = measure.applyAsLong(MANY);
        return (many - few) / (MANY - FEW);
    }

    private long countStatementsForUse(int sourceCount) {
        earnAcross(sourceCount);

        Statistics statistics = startCountingQueries();
        use(ORDER_ID, sourceCount * 100L);
        return statistics.getPrepareStatementCount();
    }

    private long countStatementsForCancelUse(int sourceCount) {
        UseResult use = useAcross(sourceCount);

        Statistics statistics = startCountingQueries();
        cancelUse(use.getPointKey(), sourceCount * 100L);
        return statistics.getPrepareStatementCount();
    }

    private long countStatementsForExpiredCancelUse(int sourceCount) {
        UseResult use = useAcross(sourceCount);
        clock.plusDays(400);

        Statistics statistics = startCountingQueries();
        cancelUse(use.getPointKey(), sourceCount * 100L);
        return statistics.getPrepareStatementCount();
    }

    private UseResult useAcross(int sourceCount) {
        earnAcross(sourceCount);
        return use(ORDER_ID, sourceCount * 100L);
    }

    private void earnAcross(int sourceCount) {
        for (int i = 0; i < sourceCount; i++) {
            earn(100, 10 + i);
        }
    }

}
