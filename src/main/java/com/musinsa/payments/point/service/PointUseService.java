package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.domain.PointUsage;
import com.musinsa.payments.point.domain.PointUsageCancellation;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.repository.PointUsageCancellationRepository;
import com.musinsa.payments.point.repository.PointUsageRepository;
import com.musinsa.payments.point.service.dto.CancelUseCommand;
import com.musinsa.payments.point.service.dto.CanceledPointDetail;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.service.dto.UsedPointDetail;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointUseService {

    private final PointTransactionRepository transactionRepository;
    private final EarnedPointRepository earnedPointRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageCancellationRepository cancellationRepository;
    private final EarnedPointReader earnedPointReader;
    private final PointPolicyReader policyReader;
    private final UserPointLocker userPointLocker;
    private final PointTransactionReader transactionReader;
    private final PointIdempotencyGuard idempotencyGuard;
    private final PointAuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional
    public UseResult use(UseCommand command) {
        userPointLocker.lock(command.getUserId());

        return idempotencyGuard.runOnce(command.getUserId(), command.getRequestKey(), PointTransactionType.USE,
                this::toUseResult,
                () -> deductPoints(command));
    }

    @Transactional
    public UseCancelResult cancelUse(CancelUseCommand command) {
        PointTransaction useTransaction = transactionReader.useByPointKey(command.getUsePointKey());
        userPointLocker.lock(useTransaction.getUserId());

        return idempotencyGuard.runOnce(useTransaction.getUserId(), command.getRequestKey(), PointTransactionType.USE_CANCEL,
                this::toUseCancelResult,
                () -> restoreUsedPoints(useTransaction, command));
    }

    private UseResult deductPoints(UseCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<EarnedPoint> sources = earnedPointReader.usableInPriorityOrder(command.getUserId());
        validateEnoughToUse(sources, command.getAmount());

        PointTransaction useTransaction = transactionRepository.save(PointTransaction.use(
                command.getUserId(), command.getAmount(), command.getOrderId(), command.getRequestKey(), now));
        deductInPriorityOrder(sources, useTransaction, now);

        return audited(useTransaction, toUseResult(useTransaction));
    }

    private void deductInPriorityOrder(List<EarnedPoint> sources, PointTransaction useTransaction, LocalDateTime now) {
        long remaining = useTransaction.getAmount();
        for (EarnedPoint source : sources) {
            if (remaining == 0) {
                break;
            }
            long deducted = Math.min(remaining, source.getRemainingAmount());
            source.deduct(deducted);
            usageRepository.save(PointUsage.of(useTransaction, source, deducted, now));
            remaining -= deducted;
        }
    }

    private UseCancelResult restoreUsedPoints(PointTransaction useTransaction, CancelUseCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PointUsage> usages = usagesOf(useTransaction);
        validateCancelable(usages, command.getAmount());

        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.useCancel(
                useTransaction, command.getAmount(), command.getRequestKey(), now));
        restoreInUsedOrder(usages, cancelTransaction, now);

        return audited(cancelTransaction, toUseCancelResult(cancelTransaction));
    }

    private void restoreInUsedOrder(List<PointUsage> usages, PointTransaction cancelTransaction, LocalDateTime now) {
        EarnedPointSources sources = earnedPointReader.sourcesOf(usages);

        long remaining = cancelTransaction.getAmount();
        for (PointUsage usage : usages) {
            if (remaining == 0) {
                break;
            }
            long restored = Math.min(remaining, usage.cancelableAmount());
            if (restored == 0) {
                continue;
            }
            usage.cancel(restored);
            giveBack(usage, sources.of(usage), restored, cancelTransaction, now);
            remaining -= restored;
        }
    }

    private void giveBack(PointUsage usage,
                          EarnedPoint source,
                          long amount,
                          PointTransaction cancelTransaction,
                          LocalDateTime now) {
        if (source.canBeRestoredAt(now)) {
            source.restore(amount);
            cancellationRepository.save(
                    PointUsageCancellation.restored(cancelTransaction, usage, source, amount, now));
            return;
        }

        EarnedPoint reissued = reissue(source, amount, cancelTransaction, now);
        cancellationRepository.save(
                PointUsageCancellation.reissued(cancelTransaction, usage, source, reissued, amount, now));
    }

    private EarnedPoint reissue(EarnedPoint expired, long amount, PointTransaction cancelTransaction, LocalDateTime now) {
        PointTransaction reissuedTransaction = transactionRepository.save(
                PointTransaction.reissuedEarn(expired, amount, cancelTransaction, now));
        LocalDateTime expireAt = now.plusDays(policyReader.current().getDefaultExpireDays());

        return earnedPointRepository.save(EarnedPoint.from(reissuedTransaction, expired.isManual(), expireAt, now));
    }

    private UseResult audited(PointTransaction transaction, UseResult result) {
        auditRecorder.recordMutation(transaction, result.getBalance());
        return result;
    }

    private UseCancelResult audited(PointTransaction transaction, UseCancelResult result) {
        auditRecorder.recordMutation(transaction, result.getBalance());
        return result;
    }

    private UseResult toUseResult(PointTransaction useTransaction) {
        return new UseResult(
                useTransaction.getPointKey(),
                useTransaction.getUserId(),
                useTransaction.getOrderId(),
                useTransaction.getAmount(),
                earnedPointReader.balanceOf(useTransaction.getUserId()),
                toUsedDetails(usagesOf(useTransaction)));
    }

    private UseCancelResult toUseCancelResult(PointTransaction cancelTransaction) {
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

    private void validateEnoughToUse(List<EarnedPoint> sources, long amount) {
        long usable = sources.stream().mapToLong(EarnedPoint::getRemainingAmount).sum();
        if (usable < amount) {
            throw PointException.of(ErrorCode.INSUFFICIENT_BALANCE,
                    "사용 가능: %d, 요청: %d".formatted(usable, amount));
        }
    }

    private void validateCancelable(List<PointUsage> usages, long amount) {
        long cancelable = totalCancelableOf(usages);
        if (amount > cancelable) {
            throw PointException.of(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                    "취소 가능: %d, 요청: %d".formatted(cancelable, amount));
        }
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
