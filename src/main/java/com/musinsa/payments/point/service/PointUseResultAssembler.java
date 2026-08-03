package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointUsage;
import com.musinsa.payments.point.domain.PointUsageCancellation;
import com.musinsa.payments.point.repository.PointUsageCancellationRepository;
import com.musinsa.payments.point.repository.PointUsageRepository;
import com.musinsa.payments.point.service.dto.CanceledPointDetail;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.service.dto.UsedPointDetail;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointUseResultAssembler {

    private final PointUsageRepository usageRepository;
    private final PointUsageCancellationRepository cancellationRepository;
    private final EarnedPointReader earnedPointReader;
    private final PointTransactionReader transactionReader;

    public UseResult toUseResult(PointTransaction useTransaction) {
        return new UseResult(
                useTransaction.getPointKey(),
                useTransaction.getUserId(),
                useTransaction.getOrderId(),
                useTransaction.getAmount(),
                earnedPointReader.balanceOf(useTransaction.getUserId()),
                toUsedDetails(usagesOf(useTransaction)));
    }

    public UseCancelResult toUseCancelResult(PointTransaction cancelTransaction) {
        PointTransaction useTransaction = transactionReader.byId(cancelTransaction.getRelatedTransactionId());

        return new UseCancelResult(
                cancelTransaction.getPointKey(),
                useTransaction.getPointKey(),
                cancelTransaction.getUserId(),
                cancelTransaction.getOrderId(),
                cancelTransaction.getAmount(),
                totalCancelableOf(usagesOf(useTransaction)),
                earnedPointReader.balanceOf(cancelTransaction.getUserId()),
                toCanceledDetails(cancelTransaction));
    }

    private List<UsedPointDetail> toUsedDetails(List<PointUsage> usages) {
        EarnedPointSources sources = earnedPointReader.sourcesOf(usages);

        return usages.stream()
                .map(usage -> new UsedPointDetail(
                        sources.earnPointKeyOf(usage),
                        usage.getAmount(),
                        sources.of(usage).isManual(),
                        sources.of(usage).getExpireAt()))
                .toList();
    }

    private List<CanceledPointDetail> toCanceledDetails(PointTransaction cancelTransaction) {
        List<PointUsageCancellation> cancellations =
                cancellationRepository.findByCancelTransactionIdOrderByIdAsc(cancelTransaction.getId());
        Map<Long, EarnedPoint> earnedPoints = earnedPointReader.byIds(referencedEarnedPointIds(cancellations));
        Map<Long, String> earnPointKeys = transactionReader.earnPointKeysByEarnedPointId(earnedPoints.values());

        return cancellations.stream()
                .map(cancellation -> toCanceledDetail(cancellation, earnedPoints, earnPointKeys))
                .toList();
    }

    private CanceledPointDetail toCanceledDetail(PointUsageCancellation cancellation,
                                                 Map<Long, EarnedPoint> earnedPoints,
                                                 Map<Long, String> earnPointKeys) {
        EarnedPoint source = earnedPoints.get(cancellation.getSourceEarnedPointId());
        EarnedPoint reissued = cancellation.isReissued()
                ? earnedPoints.get(cancellation.getReissuedEarnedPointId())
                : null;

        return new CanceledPointDetail(
                earnPointKeys.get(source.getId()),
                cancellation.getAmount(),
                cancellation.isReissued(),
                reissued == null ? null : earnPointKeys.get(reissued.getId()),
                reissued == null ? source.getExpireAt() : reissued.getExpireAt());
    }

    private List<Long> referencedEarnedPointIds(List<PointUsageCancellation> cancellations) {
        return cancellations.stream()
                .flatMap(cancellation -> Stream.of(
                        cancellation.getSourceEarnedPointId(), cancellation.getReissuedEarnedPointId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<PointUsage> usagesOf(PointTransaction useTransaction) {
        return usageRepository.findByUseTransactionIdOrderByIdAsc(useTransaction.getId());
    }

    private long totalCancelableOf(List<PointUsage> usages) {
        return usages.stream().mapToLong(PointUsage::cancelableAmount).sum();
    }
}
