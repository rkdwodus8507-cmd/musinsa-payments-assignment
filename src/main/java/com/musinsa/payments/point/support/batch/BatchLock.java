package com.musinsa.payments.point.support.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "batch_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchLock {

    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false, length = 64)
    private String holder;

    @Column(nullable = false)
    private LocalDateTime acquiredAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
