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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_lot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLot {

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
    private PointLotStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expireAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointLot create(Long transactionId,
                                  Long userId,
                                  long amount,
                                  boolean manual,
                                  LocalDateTime expireAt,
                                  LocalDateTime now) {
        PointLot lot = new PointLot();
        lot.transactionId = transactionId;
        lot.userId = userId;
        lot.originalAmount = amount;
        lot.remainingAmount = amount;
        lot.manual = manual;
        lot.status = PointLotStatus.AVAILABLE;
        lot.expireAt = expireAt;
        lot.createdAt = now;
        return lot;
    }

    public boolean isUsableAt(LocalDateTime at) {
        return status == PointLotStatus.AVAILABLE && expireAt.isAfter(at) && remainingAmount > 0;
    }

    public boolean isRestorableAt(LocalDateTime at) {
        return status == PointLotStatus.AVAILABLE && expireAt.isAfter(at);
    }

    public void use(long amount) {
        if (amount <= 0 || amount > remainingAmount) {
            throw PointException.of(ErrorCode.INTERNAL_ERROR,
                    "적립 단위 차감 금액이 올바르지 않습니다. lotId=%d, 요청=%d, 잔액=%d".formatted(id, amount, remainingAmount));
        }
        this.remainingAmount -= amount;
    }

    public void restore(long amount) {
        if (amount <= 0 || remainingAmount + amount > originalAmount) {
            throw PointException.of(ErrorCode.INTERNAL_ERROR,
                    "적립 단위 복원 금액이 올바르지 않습니다. lotId=%d, 요청=%d, 잔액=%d".formatted(id, amount, remainingAmount));
        }
        this.remainingAmount += amount;
    }

    public void cancelEarn(LocalDateTime now) {
        if (status == PointLotStatus.CANCELED) {
            throw PointException.of(ErrorCode.EARN_ALREADY_CANCELED);
        }
        if (status == PointLotStatus.EXPIRED || !expireAt.isAfter(now)) {
            throw PointException.of(ErrorCode.EARN_ALREADY_EXPIRED);
        }
        if (remainingAmount != originalAmount) {
            throw PointException.of(ErrorCode.EARN_PARTIALLY_USED,
                    "적립: %d, 잔액: %d".formatted(originalAmount, remainingAmount));
        }
        this.status = PointLotStatus.CANCELED;
        this.remainingAmount = 0;
    }

    public void expire() {
        if (status != PointLotStatus.AVAILABLE) {
            return;
        }
        this.status = PointLotStatus.EXPIRED;
    }
}
