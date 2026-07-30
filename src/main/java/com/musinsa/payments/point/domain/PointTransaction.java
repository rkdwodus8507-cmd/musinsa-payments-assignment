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
        PointTransaction transaction = newTransaction(userId, PointTransactionType.EARN, amount, now);
        transaction.memo = memo;
        transaction.requestKey = requestKey;
        return transaction;
    }

    public static PointTransaction reissuedEarn(EarnedPoint expired, long amount, PointTransaction cancelTransaction, String memo, LocalDateTime now) {
        PointTransaction transaction = newTransaction(expired.getUserId(), PointTransactionType.EARN, amount, now);
        transaction.relatedTransactionId = cancelTransaction.getId();
        transaction.memo = memo;
        return transaction;
    }

    public static PointTransaction earnCancel(PointTransaction earnTransaction, long amount, String requestKey, LocalDateTime now) {
        PointTransaction transaction = newTransaction(earnTransaction.getUserId(), PointTransactionType.EARN_CANCEL, amount, now);
        transaction.relatedTransactionId = earnTransaction.getId();
        transaction.requestKey = requestKey;
        return transaction;
    }

    public static PointTransaction use(Long userId, long amount, String orderId, String requestKey, LocalDateTime now) {
        PointTransaction transaction = newTransaction(userId, PointTransactionType.USE, amount, now);
        transaction.orderId = orderId;
        transaction.requestKey = requestKey;
        return transaction;
    }

    public static PointTransaction useCancel(PointTransaction useTransaction, long amount, String requestKey, LocalDateTime now) {
        PointTransaction transaction = newTransaction(useTransaction.getUserId(), PointTransactionType.USE_CANCEL, amount, now);
        transaction.orderId = useTransaction.getOrderId();
        transaction.relatedTransactionId = useTransaction.getId();
        transaction.requestKey = requestKey;
        return transaction;
    }

    private static PointTransaction newTransaction(Long userId, PointTransactionType type, long amount, LocalDateTime now) {
        PointTransaction transaction = new PointTransaction();
        transaction.pointKey = UUID.randomUUID().toString();
        transaction.userId = userId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.createdAt = now;
        return transaction;
    }
}
