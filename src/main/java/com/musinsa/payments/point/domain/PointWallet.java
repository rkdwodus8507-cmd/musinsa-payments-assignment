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
@Table(name = "point_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointWallet create(Long userId, LocalDateTime now) {
        PointWallet wallet = new PointWallet();
        wallet.userId = userId;
        wallet.createdAt = now;
        return wallet;
    }
}
