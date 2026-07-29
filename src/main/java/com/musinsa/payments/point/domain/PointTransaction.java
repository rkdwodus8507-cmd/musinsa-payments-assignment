package com.musinsa.payments.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(length = 64)
    private String orderId;

    private Long relatedTransactionId;

    @Column(length = 64, updatable = false)
    private String requestKey;

    @Column(length = 255)
    private String memo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointTransaction earn(Long userId, long amount, String memo, String requestKey, LocalDateTime now) {
        return of(userId, PointTransactionType.EARN, amount, null, null, requestKey, memo, now);
    }

    public static PointTransaction reissuedEarn(Long userId, long amount, Long cancelTransactionId, String memo, LocalDateTime now) {
        return of(userId, PointTransactionType.EARN, amount, null, cancelTransactionId, null, memo, now);
    }

    public static PointTransaction earnCancel(Long userId, long amount, Long earnTransactionId, String requestKey, LocalDateTime now) {
        return of(userId, PointTransactionType.EARN_CANCEL, amount, null, earnTransactionId, requestKey, null, now);
    }

    public static PointTransaction use(Long userId, long amount, String orderId, String requestKey, LocalDateTime now) {
        return of(userId, PointTransactionType.USE, amount, orderId, null, requestKey, null, now);
    }

    public static PointTransaction useCancel(Long userId, long amount, String orderId, Long useTransactionId, String requestKey, LocalDateTime now) {
        return of(userId, PointTransactionType.USE_CANCEL, amount, orderId, useTransactionId, requestKey, null, now);
    }

    public boolean isEarn() {
        return type == PointTransactionType.EARN;
    }

    public boolean isUse() {
        return type == PointTransactionType.USE;
    }

    private static PointTransaction of(Long userId,
                                       PointTransactionType type,
                                       long amount,
                                       String orderId,
                                       Long relatedTransactionId,
                                       String requestKey,
                                       String memo,
                                       LocalDateTime now) {
        PointTransaction transaction = new PointTransaction();
        transaction.pointKey = UUID.randomUUID().toString();
        transaction.userId = userId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.orderId = orderId;
        transaction.relatedTransactionId = relatedTransactionId;
        transaction.requestKey = requestKey;
        transaction.memo = memo;
        transaction.createdAt = now;
        return transaction;
    }
}
