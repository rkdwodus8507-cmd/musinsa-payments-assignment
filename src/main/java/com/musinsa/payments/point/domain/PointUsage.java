package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long useTransactionId;

    @Column(updatable = false)
    private Long earnedPointId;

    @Column(updatable = false)
    private String orderId;

    @Column(updatable = false)
    private long amount;

    private long canceledAmount;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public static PointUsage of(PointTransaction useTransaction, EarnedPoint source, long amount, LocalDateTime now) {
        PointUsage usage = new PointUsage();
        usage.useTransactionId = useTransaction.getId();
        usage.earnedPointId = source.getId();
        usage.orderId = useTransaction.getOrderId();
        usage.amount = amount;
        usage.canceledAmount = 0;
        usage.createdAt = now;
        return usage;
    }

    public long cancelableAmount() {
        return amount - canceledAmount;
    }

    public void cancel(long cancelAmount) {
        if (cancelAmount <= 0 || cancelAmount > cancelableAmount()) {
            throw PointException.of(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                    "사용: %d, 기취소: %d, 요청: %d".formatted(amount, canceledAmount, cancelAmount));
        }
        this.canceledAmount += cancelAmount;
    }
}
