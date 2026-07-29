package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "earned_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarnedPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long transactionId;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private long originalAmount;

    @Column(nullable = false)
    private long remainingAmount;

    @Column(nullable = false, updatable = false)
    private boolean manual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EarnedPointStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expireAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static EarnedPoint of(Long transactionId,
                                 Long userId,
                                 long amount,
                                 boolean manual,
                                 LocalDateTime expireAt,
                                 LocalDateTime now) {
        EarnedPoint earnedPoint = new EarnedPoint();
        earnedPoint.transactionId = transactionId;
        earnedPoint.userId = userId;
        earnedPoint.originalAmount = amount;
        earnedPoint.remainingAmount = amount;
        earnedPoint.manual = manual;
        earnedPoint.status = EarnedPointStatus.AVAILABLE;
        earnedPoint.expireAt = expireAt;
        earnedPoint.createdAt = now;
        return earnedPoint;
    }

    public boolean isExpiredAt(LocalDateTime at) {
        return status == EarnedPointStatus.EXPIRED || !expireAt.isAfter(at);
    }

    public boolean isUsableAt(LocalDateTime at) {
        return isAvailableAt(at) && remainingAmount > 0;
    }

    public boolean isRestorableAt(LocalDateTime at) {
        return isAvailableAt(at);
    }

    public void deduct(long amount) {
        if (amount <= 0 || amount > remainingAmount) {
            throw PointException.of(ErrorCode.INTERNAL_ERROR,
                    "차감 금액이 올바르지 않습니다. earnedPointId=%d, 요청=%d, 잔액=%d".formatted(id, amount, remainingAmount));
        }
        this.remainingAmount -= amount;
    }

    public void restore(long amount) {
        if (amount <= 0 || remainingAmount + amount > originalAmount) {
            throw PointException.of(ErrorCode.INTERNAL_ERROR,
                    "복원 금액이 올바르지 않습니다. earnedPointId=%d, 요청=%d, 잔액=%d".formatted(id, amount, remainingAmount));
        }
        this.remainingAmount += amount;
    }

    public void cancel(LocalDateTime now) {
        if (status == EarnedPointStatus.CANCELED) {
            throw PointException.of(ErrorCode.EARN_ALREADY_CANCELED);
        }
        if (isExpiredAt(now)) {
            throw PointException.of(ErrorCode.EARN_ALREADY_EXPIRED);
        }
        if (isPartiallyUsed()) {
            throw PointException.of(ErrorCode.EARN_PARTIALLY_USED,
                    "적립: %d, 잔액: %d".formatted(originalAmount, remainingAmount));
        }
        this.status = EarnedPointStatus.CANCELED;
        this.remainingAmount = 0;
    }

    public void expire() {
        if (status == EarnedPointStatus.AVAILABLE) {
            this.status = EarnedPointStatus.EXPIRED;
        }
    }

    private boolean isAvailableAt(LocalDateTime at) {
        return status == EarnedPointStatus.AVAILABLE && expireAt.isAfter(at);
    }

    private boolean isPartiallyUsed() {
        return remainingAmount != originalAmount;
    }
}
