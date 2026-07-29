package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import com.musinsa.payments.point.domain.PointTransaction;
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
import java.util.Map;
import java.util.stream.Collectors;
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
        Map<Long, String> earnPointKeys = earnedPointReader.earnPointKeyByEarnedPointId(earnedPoints);

        return new BalanceResult(
                userId,
                sumUsable(earnedPoints, now, false),
                sumUsable(earnedPoints, now, true),
                toSummaries(earnedPoints, earnPointKeys, now));
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
        Map<Long, EarnedPoint> sources = earnedPointReader.byIds(
                usages.stream().map(PointUsage::getEarnedPointId).distinct().toList());
        Map<Long, String> earnPointKeys = earnedPointReader.earnPointKeyByEarnedPointId(sources.values());
        Map<Long, String> usePointKeys = usePointKeysOf(usages);

        List<OrderUsageDetail> details = usages.stream()
                .map(usage -> {
                    EarnedPoint source = sources.get(usage.getEarnedPointId());
                    return new OrderUsageDetail(
                            usePointKeys.get(usage.getUseTransactionId()),
                            earnPointKeys.get(source.getId()),
                            usage.getAmount(),
                            usage.getCanceledAmount(),
                            source.isManual(),
                            source.getExpireAt());
                })
                .toList();

        return new OrderUsageResult(
                orderId,
                usages.stream().mapToLong(PointUsage::getAmount).sum(),
                usages.stream().mapToLong(PointUsage::getCanceledAmount).sum(),
                details);
    }

    private long sumUsable(List<EarnedPoint> earnedPoints, LocalDateTime now, boolean manualOnly) {
        return earnedPoints.stream()
                .filter(earnedPoint -> earnedPoint.isUsableAt(now))
                .filter(earnedPoint -> !manualOnly || earnedPoint.isManual())
                .mapToLong(EarnedPoint::getRemainingAmount)
                .sum();
    }

    private List<EarnedPointSummary> toSummaries(List<EarnedPoint> earnedPoints,
                                                 Map<Long, String> earnPointKeys,
                                                 LocalDateTime now) {
        return earnedPoints.stream()
                .map(earnedPoint -> new EarnedPointSummary(
                        earnPointKeys.get(earnedPoint.getId()),
                        earnedPoint.getOriginalAmount(),
                        earnedPoint.getRemainingAmount(),
                        earnedPoint.isManual(),
                        displayStatusOf(earnedPoint, now),
                        earnedPoint.getExpireAt()))
                .toList();
    }

    private Map<Long, String> usePointKeysOf(List<PointUsage> usages) {
        List<Long> transactionIds = usages.stream()
                .map(PointUsage::getUseTransactionId)
                .distinct()
                .toList();
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return transactionRepository.findByIdIn(transactionIds).stream()
                .collect(Collectors.toMap(PointTransaction::getId, PointTransaction::getPointKey));
    }

    private String displayStatusOf(EarnedPoint earnedPoint, LocalDateTime now) {
        return earnedPoint.isExpiredAt(now)
                ? EarnedPointStatus.EXPIRED.name()
                : earnedPoint.getStatus().name();
    }
}
