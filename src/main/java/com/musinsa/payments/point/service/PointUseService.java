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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final PointPolicyService policyService;
    private final UserPointLocker userPointLocker;
    private final PointIdempotencyGuard idempotencyGuard;
    private final Clock clock;

    @Transactional
    public UseResult use(UseCommand command) {
        userPointLocker.lock(command.userId());

        return idempotencyGuard.findHandled(command.userId(), command.requestKey(), PointTransactionType.USE)
                .map(this::toUseResult)
                .orElseGet(() -> deductPoints(command));
    }

    @Transactional
    public UseCancelResult cancelUse(CancelUseCommand command) {
        PointTransaction useTransaction = findUseTransaction(command.usePointKey());
        userPointLocker.lock(useTransaction.getUserId());

        return idempotencyGuard.findHandled(useTransaction.getUserId(), command.requestKey(), PointTransactionType.USE_CANCEL)
                .map(this::toUseCancelResult)
                .orElseGet(() -> restoreUsedPoints(useTransaction, command));
    }

    private UseResult deductPoints(UseCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<EarnedPoint> usable = earnedPointReader.usableInPriorityOrder(command.userId());
        long available = usable.stream().mapToLong(EarnedPoint::getRemainingAmount).sum();
        if (available < command.amount()) {
            throw PointException.of(ErrorCode.INSUFFICIENT_BALANCE,
                    "사용 가능: %d, 요청: %d".formatted(available, command.amount()));
        }

        PointTransaction useTransaction = transactionRepository.save(PointTransaction.use(
                command.userId(), command.amount(), command.orderId(), command.requestKey(), now));
        deductInPriorityOrder(usable, useTransaction, command, now);

        return toUseResult(useTransaction);
    }

    private void deductInPriorityOrder(List<EarnedPoint> usable,
                                       PointTransaction useTransaction,
                                       UseCommand command,
                                       LocalDateTime now) {
        long unassigned = command.amount();
        for (EarnedPoint earnedPoint : usable) {
            if (unassigned == 0) {
                break;
            }
            long deducted = Math.min(unassigned, earnedPoint.getRemainingAmount());
            earnedPoint.deduct(deducted);
            usageRepository.save(PointUsage.of(
                    useTransaction.getId(), earnedPoint.getId(), command.orderId(), deducted, now));
            unassigned -= deducted;
        }
    }

    private UseCancelResult restoreUsedPoints(PointTransaction useTransaction, CancelUseCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PointUsage> usages = usagesOf(useTransaction);
        long cancelable = usages.stream().mapToLong(PointUsage::cancelableAmount).sum();
        if (command.amount() > cancelable) {
            throw PointException.of(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                    "취소 가능: %d, 요청: %d".formatted(cancelable, command.amount()));
        }

        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.useCancel(
                useTransaction.getUserId(), command.amount(), useTransaction.getOrderId(),
                useTransaction.getId(), command.requestKey(), now));
        restoreInUsedOrder(usages, command.amount(), cancelTransaction, now);

        return toUseCancelResult(cancelTransaction);
    }

    private void restoreInUsedOrder(List<PointUsage> usages,
                                    long cancelAmount,
                                    PointTransaction cancelTransaction,
                                    LocalDateTime now) {
        long unassigned = cancelAmount;
        for (PointUsage usage : usages) {
            if (unassigned == 0) {
                break;
            }
            long restorable = Math.min(unassigned, usage.cancelableAmount());
            if (restorable == 0) {
                continue;
            }
            usage.cancel(restorable);
            restoreOrReissue(usage, restorable, cancelTransaction, now);
            unassigned -= restorable;
        }
    }

    private void restoreOrReissue(PointUsage usage,
                                  long amount,
                                  PointTransaction cancelTransaction,
                                  LocalDateTime now) {
        EarnedPoint source = earnedPointRepository.findById(usage.getEarnedPointId())
                .orElseThrow(() -> PointException.of(ErrorCode.EARNED_POINT_NOT_FOUND, "id=" + usage.getEarnedPointId()));

        if (source.isRestorableAt(now)) {
            source.restore(amount);
            cancellationRepository.save(PointUsageCancellation.restored(
                    cancelTransaction.getId(), usage.getId(), amount, source.getId(), now));
            return;
        }

        PointTransaction reissuedTransaction = transactionRepository.save(PointTransaction.reissuedEarn(
                source.getUserId(), amount, cancelTransaction.getId(), REISSUE_MEMO, now));
        EarnedPoint reissued = earnedPointRepository.save(EarnedPoint.of(
                reissuedTransaction.getId(), source.getUserId(), amount, source.isManual(),
                now.plusDays(policyService.getPolicy().getDefaultExpireDays()), now));
        cancellationRepository.save(PointUsageCancellation.reissued(
                cancelTransaction.getId(), usage.getId(), amount, reissued.getId(), now));
    }

    private UseResult toUseResult(PointTransaction useTransaction) {
        List<PointUsage> usages = usagesOf(useTransaction);
        Map<Long, EarnedPoint> sources = earnedPointReader.byIds(
                usages.stream().map(PointUsage::getEarnedPointId).distinct().toList());
        Map<Long, String> earnPointKeys = earnedPointReader.earnPointKeyByEarnedPointId(sources.values());

        List<UsedPointDetail> details = usages.stream()
                .map(usage -> {
                    EarnedPoint source = sources.get(usage.getEarnedPointId());
                    return new UsedPointDetail(
                            earnPointKeys.get(source.getId()), usage.getAmount(), source.isManual(), source.getExpireAt());
                })
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
        PointTransaction useTransaction = findTransaction(cancelTransaction.getRelatedTransactionId());
        long remainingCancelable = usagesOf(useTransaction).stream()
                .mapToLong(PointUsage::cancelableAmount)
                .sum();

        return new UseCancelResult(
                cancelTransaction.getPointKey(),
                useTransaction.getPointKey(),
                cancelTransaction.getUserId(),
                cancelTransaction.getOrderId(),
                cancelTransaction.getAmount(),
                remainingCancelable,
                earnedPointReader.balanceOf(cancelTransaction.getUserId()),
                toCanceledDetails(cancelTransaction));
    }

    private List<CanceledPointDetail> toCanceledDetails(PointTransaction cancelTransaction) {
        List<PointUsageCancellation> cancellations =
                cancellationRepository.findByCancelTransactionIdOrderByIdAsc(cancelTransaction.getId());
        Map<Long, PointUsage> usages = usageRepository.findByIdIn(
                        cancellations.stream().map(PointUsageCancellation::getPointUsageId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PointUsage::getId, Function.identity()));

        List<Long> earnedPointIds = cancellations.stream()
                .flatMap(cancellation -> Stream.of(
                        usages.get(cancellation.getPointUsageId()).getEarnedPointId(),
                        cancellation.getReissuedEarnedPointId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, EarnedPoint> earnedPoints = earnedPointReader.byIds(earnedPointIds);
        Map<Long, String> earnPointKeys = earnedPointReader.earnPointKeyByEarnedPointId(earnedPoints.values());

        List<CanceledPointDetail> details = new ArrayList<>();
        for (PointUsageCancellation cancellation : cancellations) {
            EarnedPoint source = earnedPoints.get(usages.get(cancellation.getPointUsageId()).getEarnedPointId());
            EarnedPoint reissued = cancellation.isReissued()
                    ? earnedPoints.get(cancellation.getReissuedEarnedPointId())
                    : null;
            details.add(new CanceledPointDetail(
                    earnPointKeys.get(source.getId()),
                    cancellation.getAmount(),
                    cancellation.isReissued(),
                    reissued == null ? null : earnPointKeys.get(reissued.getId()),
                    reissued == null ? source.getExpireAt() : reissued.getExpireAt()));
        }
        return details;
    }

    private List<PointUsage> usagesOf(PointTransaction useTransaction) {
        return usageRepository.findByUseTransactionIdOrderByIdAsc(useTransaction.getId());
    }

    private PointTransaction findUseTransaction(String pointKey) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
        if (!transaction.isUse()) {
            throw PointException.of(ErrorCode.NOT_USE_TRANSACTION, "type=" + transaction.getType());
        }
        return transaction;
    }

    private PointTransaction findTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "transactionId=" + transactionId));
    }
}
