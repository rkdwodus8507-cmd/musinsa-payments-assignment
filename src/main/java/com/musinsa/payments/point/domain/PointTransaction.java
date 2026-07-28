package com.musinsa.payments.point.domain;

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
import java.util.UUID;

@Entity
@Table(name = "point_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 36)
    private String pointKey;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(length = 64)
    private String orderId;

    private Long relatedTransactionId;

    @Column(length = 255)
    private String memo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointTransaction earn(Long userId, long amount, String memo, LocalDateTime now) {
        return create(userId, PointTransactionType.EARN, amount, null, null, memo, now);
    }

    public static PointTransaction reissuedEarn(Long userId, long amount, Long cancelTransactionId, String memo, LocalDateTime now) {
        return create(userId, PointTransactionType.EARN, amount, null, cancelTransactionId, memo, now);
    }

    public static PointTransaction earnCancel(Long userId, long amount, Long earnTransactionId, LocalDateTime now) {
        return create(userId, PointTransactionType.EARN_CANCEL, amount, null, earnTransactionId, null, now);
    }

    public static PointTransaction use(Long userId, long amount, String orderId, LocalDateTime now) {
        return create(userId, PointTransactionType.USE, amount, orderId, null, null, now);
    }

    public static PointTransaction useCancel(Long userId, long amount, String orderId, Long useTransactionId, LocalDateTime now) {
        return create(userId, PointTransactionType.USE_CANCEL, amount, orderId, useTransactionId, null, now);
    }

    public boolean isEarn() {
        return type == PointTransactionType.EARN;
    }

    public boolean isUse() {
        return type == PointTransactionType.USE;
    }

    private static PointTransaction create(Long userId,
                                           PointTransactionType type,
                                           long amount,
                                           String orderId,
                                           Long relatedTransactionId,
                                           String memo,
                                           LocalDateTime now) {
        PointTransaction transaction = new PointTransaction();
        transaction.pointKey = UUID.randomUUID().toString();
        transaction.userId = userId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.orderId = orderId;
        transaction.relatedTransactionId = relatedTransactionId;
        transaction.memo = memo;
        transaction.createdAt = now;
        return transaction;
    }
}
