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

    @Column(nullable = false, updatable = false)
    private Long useTransactionId;

    @Column(nullable = false, updatable = false)
    private Long earnedPointId;

    @Column(nullable = false, updatable = false, length = 64)
    private String orderId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false)
    private long canceledAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointUsage of(Long useTransactionId,
                                Long earnedPointId,
                                String orderId,
                                long amount,
                                LocalDateTime now) {
        PointUsage usage = new PointUsage();
        usage.useTransactionId = useTransactionId;
        usage.earnedPointId = earnedPointId;
        usage.orderId = orderId;
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
