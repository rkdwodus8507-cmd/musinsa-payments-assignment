package com.musinsa.payments.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_lot_usage_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLotUsageCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long cancelTransactionId;

    @Column(nullable = false, updatable = false)
    private Long lotUsageId;

    @Column(nullable = false, updatable = false)
    private long amount;

    private Long restoredLotId;

    private Long reissuedLotId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointLotUsageCancel restored(Long cancelTransactionId, Long lotUsageId, long amount, Long lotId, LocalDateTime now) {
        PointLotUsageCancel cancel = base(cancelTransactionId, lotUsageId, amount, now);
        cancel.restoredLotId = lotId;
        return cancel;
    }

    public static PointLotUsageCancel reissued(Long cancelTransactionId, Long lotUsageId, long amount, Long newLotId, LocalDateTime now) {
        PointLotUsageCancel cancel = base(cancelTransactionId, lotUsageId, amount, now);
        cancel.reissuedLotId = newLotId;
        return cancel;
    }

    public boolean isReissued() {
        return reissuedLotId != null;
    }

    private static PointLotUsageCancel base(Long cancelTransactionId, Long lotUsageId, long amount, LocalDateTime now) {
        PointLotUsageCancel cancel = new PointLotUsageCancel();
        cancel.cancelTransactionId = cancelTransactionId;
        cancel.lotUsageId = lotUsageId;
        cancel.amount = amount;
        cancel.createdAt = now;
        return cancel;
    }
}
