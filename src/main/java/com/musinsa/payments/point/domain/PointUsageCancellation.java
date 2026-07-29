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

    private Long restoredEarnedPointId;

    private Long reissuedEarnedPointId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointUsageCancellation restored(Long cancelTransactionId,
                                                  Long pointUsageId,
                                                  long amount,
                                                  Long restoredEarnedPointId,
                                                  LocalDateTime now) {
        PointUsageCancellation cancellation = of(cancelTransactionId, pointUsageId, amount, now);
        cancellation.restoredEarnedPointId = restoredEarnedPointId;
        return cancellation;
    }

    public static PointUsageCancellation reissued(Long cancelTransactionId,
                                                  Long pointUsageId,
                                                  long amount,
                                                  Long reissuedEarnedPointId,
                                                  LocalDateTime now) {
        PointUsageCancellation cancellation = of(cancelTransactionId, pointUsageId, amount, now);
        cancellation.reissuedEarnedPointId = reissuedEarnedPointId;
        return cancellation;
    }

    public boolean isReissued() {
        return reissuedEarnedPointId != null;
    }

    private static PointUsageCancellation of(Long cancelTransactionId,
                                             Long pointUsageId,
                                             long amount,
                                             LocalDateTime now) {
        PointUsageCancellation cancellation = new PointUsageCancellation();
        cancellation.cancelTransactionId = cancelTransactionId;
        cancellation.pointUsageId = pointUsageId;
        cancellation.amount = amount;
        cancellation.createdAt = now;
        return cancellation;
    }
}
