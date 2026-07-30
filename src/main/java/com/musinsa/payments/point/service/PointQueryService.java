package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.domain.PointUsage;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.repository.PointUsageRepository;
import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.service.dto.EarnedPointSummary;
import com.musinsa.payments.point.service.dto.OrderUsageDetail;
import com.musinsa.payments.point.service.dto.OrderUsageResult;
import com.musinsa.payments.point.service.dto.TransactionResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

    private final PointTransactionRepository transactionRepository;
    private final PointUsageRepository usageRepository;
    private final EarnedPointReader earnedPointReader;
    private final Clock clock;

    public BalanceResult getBalance(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<EarnedPoint> earnedPoints = earnedPointReader.allOf(userId);

        List<EarnedPoint> usable = earnedPoints.stream()
                .filter(earnedPoint -> earnedPoint.canBeUsedAt(now))
                .toList();
        List<EarnedPoint> usableManual = usable.stream()
                .filter(EarnedPoint::isManual)
                .toList();

        return new BalanceResult(userId, sumRemaining(usable), sumRemaining(usableManual), toSummaries(earnedPoints, now));
    }

    public Page<TransactionResult> getTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByIdDesc(userId, pageable)
                .map(transaction -> new TransactionResult(
                        transaction.getPointKey(),
                        transaction.getType().name(),
                        transaction.getAmount(),
                        transaction.getOrderId(),
                        transaction.getMemo(),
                        transaction.getCreatedAt()));
    }

    public OrderUsageResult getOrderUsage(String orderId) {
        List<PointUsage> usages = usageRepository.findByOrderIdOrderByIdAsc(orderId);

        List<OrderUsageDetail> details = usages.stream()
                .map(usage -> new OrderUsageDetail(
                        usage.getUsePointKey(),
                        usage.getEarnedPointKey(),
                        usage.getAmount(),
                        usage.getCanceledAmount(),
                        usage.isEarnedPointManual(),
                        usage.getEarnedPointExpireAt()))
                .toList();

        return new OrderUsageResult(
                orderId,
                usages.stream().mapToLong(PointUsage::getAmount).sum(),
                usages.stream().mapToLong(PointUsage::getCanceledAmount).sum(),
                details);
    }

    private long sumRemaining(List<EarnedPoint> earnedPoints) {
        return earnedPoints.stream().mapToLong(EarnedPoint::getRemainingAmount).sum();
    }

    private List<EarnedPointSummary> toSummaries(List<EarnedPoint> earnedPoints, LocalDateTime now) {
        return earnedPoints.stream()
                .map(earnedPoint -> new EarnedPointSummary(
                        earnedPoint.getPointKey(),
                        earnedPoint.getOriginalAmount(),
                        earnedPoint.getRemainingAmount(),
                        earnedPoint.isManual(),
                        displayStatusOf(earnedPoint, now),
                        earnedPoint.getExpireAt()))
                .toList();
    }

    private String displayStatusOf(EarnedPoint earnedPoint, LocalDateTime now) {
        return earnedPoint.isExpiredAt(now)
                ? EarnedPointStatus.EXPIRED.name()
                : earnedPoint.getStatus().name();
    }
}
