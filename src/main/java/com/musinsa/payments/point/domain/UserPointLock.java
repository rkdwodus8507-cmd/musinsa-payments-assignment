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
@Table(name = "user_point_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long userId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public static UserPointLock of(Long userId, LocalDateTime now) {
        UserPointLock lock = new UserPointLock();
        lock.userId = userId;
        lock.createdAt = now;
        return lock;
    }
}
