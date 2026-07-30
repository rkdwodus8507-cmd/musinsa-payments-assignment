package com.musinsa.payments.point.domain;

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
@Table(name = "point_usage_cancellation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long cancelTransactionId;

    @Column(nullable = false, updatable = false)
    private Long pointUsageId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private Long sourceEarnedPointId;

    @Column(nullable = false, updatable = false, length = 36)
    private String sourcePointKey;

    @Column(updatable = false)
    private Long reissuedEarnedPointId;

    @Column(updatable = false, length = 36)
    private String reissuedPointKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expireAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointUsageCancellation restored(PointTransaction cancelTransaction,
                                                  PointUsage usage,
                                                  EarnedPoint source,
                                                  long amount,
                                                  LocalDateTime now) {
        PointUsageCancellation cancellation = newCancellation(cancelTransaction, usage, source, amount, now);
        cancellation.expireAt = source.getExpireAt();
        return cancellation;
    }

    public static PointUsageCancellation reissued(PointTransaction cancelTransaction,
                                                  PointUsage usage,
                                                  EarnedPoint source,
                                                  EarnedPoint reissued,
                                                  long amount,
                                                  LocalDateTime now) {
        PointUsageCancellation cancellation = newCancellation(cancelTransaction, usage, source, amount, now);
        cancellation.reissuedEarnedPointId = reissued.getId();
        cancellation.reissuedPointKey = reissued.getPointKey();
        cancellation.expireAt = reissued.getExpireAt();
        return cancellation;
    }

    public boolean isReissued() {
        return reissuedEarnedPointId != null;
    }

    private static PointUsageCancellation newCancellation(PointTransaction cancelTransaction,
                                                          PointUsage usage,
                                                          EarnedPoint source,
                                                          long amount,
                                                          LocalDateTime now) {
        PointUsageCancellation cancellation = new PointUsageCancellation();
        cancellation.cancelTransactionId = cancelTransaction.getId();
        cancellation.pointUsageId = usage.getId();
        cancellation.amount = amount;
        cancellation.sourceEarnedPointId = source.getId();
        cancellation.sourcePointKey = source.getPointKey();
        cancellation.createdAt = now;
        return cancellation;
    }
}
