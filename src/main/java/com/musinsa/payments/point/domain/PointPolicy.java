package com.musinsa.payments.point.domain;

import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointPolicy {

    public static final long SINGLETON_ID = 1L;

    private static final int MAX_ALLOWED_EXPIRE_DAYS = 1824;

    @Id
    private Long id;

    @Column(nullable = false)
    private long minEarnAmount;

    @Column(nullable = false)
    private long maxEarnAmount;

    @Column(nullable = false)
    private long maxUserBalance;

    @Column(nullable = false)
    private int defaultExpireDays;

    @Column(nullable = false)
    private int minExpireDays;

    @Column(nullable = false)
    private int maxExpireDays;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PointPolicy create(long minEarnAmount,
                                     long maxEarnAmount,
                                     long maxUserBalance,
                                     int defaultExpireDays,
                                     int minExpireDays,
                                     int maxExpireDays,
                                     LocalDateTime now) {
        PointPolicy policy = new PointPolicy();
        policy.id = SINGLETON_ID;
        policy.apply(minEarnAmount, maxEarnAmount, maxUserBalance, defaultExpireDays, minExpireDays, maxExpireDays, now);
        return policy;
    }

    public void update(long minEarnAmount,
                       long maxEarnAmount,
                       long maxUserBalance,
                       int defaultExpireDays,
                       int minExpireDays,
                       int maxExpireDays,
                       LocalDateTime now) {
        apply(minEarnAmount, maxEarnAmount, maxUserBalance, defaultExpireDays, minExpireDays, maxExpireDays, now);
    }

    public void validateEarnAmount(long amount) {
        if (amount < minEarnAmount || amount > maxEarnAmount) {
            throw PointException.of(ErrorCode.INVALID_EARN_AMOUNT,
                    "허용 범위: %d ~ %d, 요청: %d".formatted(minEarnAmount, maxEarnAmount, amount));
        }
    }

    public int resolveExpireDays(Integer requestedExpireDays) {
        if (requestedExpireDays == null) {
            return defaultExpireDays;
        }
        if (requestedExpireDays < minExpireDays || requestedExpireDays > maxExpireDays) {
            throw PointException.of(ErrorCode.INVALID_EXPIRE_DAYS,
                    "허용 범위: %d ~ %d, 요청: %d".formatted(minExpireDays, maxExpireDays, requestedExpireDays));
        }
        return requestedExpireDays;
    }

    public void validateBalanceAfterEarn(long currentBalance, long earnAmount) {
        long expected = currentBalance + earnAmount;
        if (expected > maxUserBalance) {
            throw PointException.of(ErrorCode.MAX_BALANCE_EXCEEDED,
                    "최대 보유: %d, 현재 잔액: %d, 요청: %d".formatted(maxUserBalance, currentBalance, earnAmount));
        }
    }

    private void apply(long minEarnAmount,
                       long maxEarnAmount,
                       long maxUserBalance,
                       int defaultExpireDays,
                       int minExpireDays,
                       int maxExpireDays,
                       LocalDateTime now) {
        if (minEarnAmount < 1 || maxEarnAmount < minEarnAmount) {
            throw PointException.of(ErrorCode.INVALID_POLICY, "적립 금액 범위가 올바르지 않습니다.");
        }
        if (maxUserBalance < maxEarnAmount) {
            throw PointException.of(ErrorCode.INVALID_POLICY, "최대 보유 포인트는 1회 최대 적립 포인트보다 커야 합니다.");
        }
        if (minExpireDays < 1 || maxExpireDays < minExpireDays) {
            throw PointException.of(ErrorCode.INVALID_POLICY, "만료일 범위가 올바르지 않습니다.");
        }
        if (maxExpireDays > MAX_ALLOWED_EXPIRE_DAYS) {
            throw PointException.of(ErrorCode.INVALID_POLICY,
                    "만료일은 5년(%d일) 미만이어야 합니다.".formatted(MAX_ALLOWED_EXPIRE_DAYS + 1));
        }
        if (defaultExpireDays < minExpireDays || defaultExpireDays > maxExpireDays) {
            throw PointException.of(ErrorCode.INVALID_POLICY, "기본 만료일이 허용 범위를 벗어났습니다.");
        }
        this.minEarnAmount = minEarnAmount;
        this.maxEarnAmount = maxEarnAmount;
        this.maxUserBalance = maxUserBalance;
        this.defaultExpireDays = defaultExpireDays;
        this.minExpireDays = minExpireDays;
        this.maxExpireDays = maxExpireDays;
        this.updatedAt = now;
    }
}
