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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointUseService {

    private static final String REISSUE_MEMO = "만료 포인트 사용취소로 인한 신규 적립";

    private final PointTransactionRepository transactionRepository;
    private final EarnedPointRepository earnedPointRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageCancellationRepository cancellationRepository;
    private final EarnedPointReader earnedPointReader;
    private final PointPolicyReader policyReader;
    private final UserPointLocker userPointLocker;
    private final PointTransactionReader transactionReader;
    private final PointIdempotencyGuard idempotencyGuard;
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

        long usable = sources.stream().mapToLong(EarnedPoint::getRemainingAmount).sum();
        if (usable < command.getAmount()) {
            throw PointException.of(ErrorCode.INSUFFICIENT_BALANCE,
                    "사용 가능: %d, 요청: %d".formatted(usable, command.getAmount()));
        }

        PointTransaction useTransaction = transactionRepository.save(PointTransaction.use(
                command.getUserId(), command.getAmount(), command.getOrderId(), command.getRequestKey(), now));
        deductInPriorityOrder(sources, useTransaction, now);

        return toUseResult(useTransaction);
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

        long cancelable = totalCancelableOf(usages);
        if (command.getAmount() > cancelable) {
            throw PointException.of(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                    "취소 가능: %d, 요청: %d".formatted(cancelable, command.getAmount()));
        }

        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.useCancel(
                useTransaction, command.getAmount(), command.getRequestKey(), now));
        restoreInUsedOrder(usages, cancelTransaction, now);

        return toUseCancelResult(cancelTransaction);
    }

    private void restoreInUsedOrder(List<PointUsage> usages, PointTransaction cancelTransaction, LocalDateTime now) {
        Map<Long, EarnedPoint> sources = earnedPointReader.byIds(
                usages.stream().map(PointUsage::getEarnedPointId).distinct().toList());

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
            giveBack(usage, sources.get(usage.getEarnedPointId()), restored, cancelTransaction, now);
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
                PointTransaction.reissuedEarn(expired, amount, cancelTransaction, REISSUE_MEMO, now));
        LocalDateTime expireAt = now.plusDays(policyReader.current().getDefaultExpireDays());

        return earnedPointRepository.save(EarnedPoint.from(reissuedTransaction, expired.isManual(), expireAt, now));
    }

    private UseResult toUseResult(PointTransaction useTransaction) {
        List<UsedPointDetail> details = usagesOf(useTransaction).stream()
                .map(usage -> new UsedPointDetail(
                        usage.getEarnedPointKey(),
                        usage.getAmount(),
                        usage.isEarnedPointManual(),
                        usage.getEarnedPointExpireAt()))
                .toList();

        return new UseResult(
                useTransaction.getPointKey(),
                useTransaction.getUserId(),
                useTransaction.getOrderId(),
                useTransaction.getAmount(),
                earnedPointReader.balanceOf(useTransaction.getUserId()),
                details);
    }

    private UseCancelResult toUseCancelResult(PointTransaction cancelTransaction) {
        PointTransaction useTransaction = transactionReader.byId(cancelTransaction.getRelatedTransactionId());
        List<CanceledPointDetail> details =
                cancellationRepository.findByCancelTransactionIdOrderByIdAsc(cancelTransaction.getId()).stream()
                        .map(cancellation -> new CanceledPointDetail(
                                cancellation.getSourcePointKey(),
                                cancellation.getAmount(),
                                cancellation.isReissued(),
                                cancellation.getReissuedPointKey(),
                                cancellation.getExpireAt()))
                        .toList();

        return new UseCancelResult(
                cancelTransaction.getPointKey(),
                useTransaction.getPointKey(),
                cancelTransaction.getUserId(),
                cancelTransaction.getOrderId(),
                cancelTransaction.getAmount(),
                totalCancelableOf(usagesOf(useTransaction)),
                earnedPointReader.balanceOf(cancelTransaction.getUserId()),
                details);
    }

    private List<PointUsage> usagesOf(PointTransaction useTransaction) {
        return usageRepository.findByUseTransactionIdOrderByIdAsc(useTransaction.getId());
    }

    private long totalCancelableOf(List<PointUsage> usages) {
        return usages.stream().mapToLong(PointUsage::cancelableAmount).sum();
    }

}
