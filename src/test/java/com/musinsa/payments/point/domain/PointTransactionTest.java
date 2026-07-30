package com.musinsa.payments.point.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("포인트 거래 - 종류별 생성 규칙")
class PointTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Test
    @DisplayName("적립 거래는 메모와 requestKey 를 담는다")
    void earn() {
        PointTransaction transaction = PointTransaction.earn(1L, 1000, "이벤트", "req-1", NOW);

        assertThat(transaction.getType()).isEqualTo(PointTransactionType.EARN);
        assertThat(transaction.getAmount()).isEqualTo(1000);
        assertThat(transaction.getMemo()).isEqualTo("이벤트");
        assertThat(transaction.getRequestKey()).isEqualTo("req-1");
        assertThat(transaction.getOrderId()).isNull();
        assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("사용 거래는 주문번호를 담는다")
    void use() {
        PointTransaction transaction = PointTransaction.use(1L, 500, "A1234", "req-2", NOW);

        assertThat(transaction.getType()).isEqualTo(PointTransactionType.USE);
        assertThat(transaction.getOrderId()).isEqualTo("A1234");
    }

    @Test
    @DisplayName("사용취소 거래는 원 사용거래의 주문번호를 승계한다")
    void useCancel() {
        PointTransaction useTransaction = PointTransaction.use(1L, 500, "A1234", null, NOW);

        PointTransaction cancelTransaction = PointTransaction.useCancel(useTransaction, 200, "req-3", NOW);

        assertThat(cancelTransaction.getType()).isEqualTo(PointTransactionType.USE_CANCEL);
        assertThat(cancelTransaction.getOrderId()).isEqualTo("A1234");
        assertThat(cancelTransaction.getUserId()).isEqualTo(1L);
        assertThat(cancelTransaction.getAmount()).isEqualTo(200);
    }

    @Test
    @DisplayName("적립취소 거래는 원 적립거래의 사용자를 승계한다")
    void earnCancel() {
        PointTransaction earnTransaction = PointTransaction.earn(7L, 1000, null, null, NOW);

        PointTransaction cancelTransaction = PointTransaction.earnCancel(earnTransaction, 1000, "req-4", NOW);

        assertThat(cancelTransaction.getType()).isEqualTo(PointTransactionType.EARN_CANCEL);
        assertThat(cancelTransaction.getUserId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("pointKey 는 거래마다 새로 발급된다")
    void pointKeyIsUnique() {
        PointTransaction first = PointTransaction.earn(1L, 1000, null, null, NOW);
        PointTransaction second = PointTransaction.earn(1L, 1000, null, null, NOW);

        assertThat(first.getPointKey()).isNotBlank().isNotEqualTo(second.getPointKey());
    }
}
