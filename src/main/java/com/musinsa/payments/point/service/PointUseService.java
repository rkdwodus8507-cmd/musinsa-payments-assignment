package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.domain.PointLotUsage;
import com.musinsa.payments.point.domain.PointLotUsageCancel;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.repository.PointLotRepository;
import com.musinsa.payments.point.repository.PointLotUsageCancelRepository;
import com.musinsa.payments.point.repository.PointLotUsageRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.service.dto.CanceledLot;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.service.dto.UseResult;
import com.musinsa.payments.point.service.dto.UsedLot;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointUseService {

    private static final String REISSUE_MEMO = "만료 포인트 사용취소로 인한 신규 적립";

    private final PointTransactionRepository transactionRepository;
    private final PointLotRepository lotRepository;
    private final PointLotUsageRepository lotUsageRepository;
    private final PointLotUsageCancelRepository lotUsageCancelRepository;
    private final PointPolicyService policyService;
    private final PointWalletLocker walletLocker;
    private final Clock clock;

    @Transactional
    public UseResult use(UseCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);

        walletLocker.lock(command.userId());

        List<PointLot> usableLots = lotRepository.findUsableLots(command.userId(), PointLotStatus.AVAILABLE, now);
        long available = usableLots.stream().mapToLong(PointLot::getRemainingAmount).sum();
        if (available < command.amount()) {
            throw PointException.of(ErrorCode.INSUFFICIENT_BALANCE,
                    "사용 가능: %d, 요청: %d".formatted(available, command.amount()));
        }

        PointTransaction useTransaction = transactionRepository.save(
                PointTransaction.use(command.userId(), command.amount(), command.orderId(), now));

        List<UsedLot> details = new ArrayList<>();
        long unassigned = command.amount();
        for (PointLot lot : usableLots) {
            if (unassigned == 0) {
                break;
            }
            long deducted = Math.min(unassigned, lot.getRemainingAmount());
            lot.use(deducted);
            lotUsageRepository.save(PointLotUsage.create(
                    useTransaction.getId(), lot.getId(), command.orderId(), deducted, now));
            details.add(new UsedLot(
                    earnPointKeyOf(lot), deducted, lot.isManual(), lot.getExpireAt()));
            unassigned -= deducted;
        }

        return new UseResult(
                useTransaction.getPointKey(),
                command.userId(),
                command.orderId(),
                command.amount(),
                available - command.amount(),
                details);
    }

    @Transactional
    public UseCancelResult cancelUse(String pointKey, long cancelAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointTransaction useTransaction = transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
        if (!useTransaction.isUse()) {
            throw PointException.of(ErrorCode.NOT_USE_TRANSACTION, "type=" + useTransaction.getType());
        }

        Long userId = useTransaction.getUserId();
        walletLocker.lock(userId);

        List<PointLotUsage> usages = lotUsageRepository.findByUseTransactionIdOrderByIdAsc(useTransaction.getId());
        long cancelable = usages.stream().mapToLong(PointLotUsage::cancelableAmount).sum();
        if (cancelAmount > cancelable) {
            throw PointException.of(ErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                    "취소 가능: %d, 요청: %d".formatted(cancelable, cancelAmount));
        }

        PointPolicy policy = policyService.getPolicy();
        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.useCancel(
                userId, cancelAmount, useTransaction.getOrderId(), useTransaction.getId(), now));

        List<CanceledLot> details = new ArrayList<>();
        long unassigned = cancelAmount;
        for (PointLotUsage usage : usages) {
            if (unassigned == 0) {
                break;
            }
            long restorable = Math.min(unassigned, usage.cancelableAmount());
            if (restorable == 0) {
                continue;
            }
            usage.cancel(restorable);
            details.add(restoreOrReissue(usage, restorable, cancelTransaction, policy, now));
            unassigned -= restorable;
        }

        return new UseCancelResult(
                cancelTransaction.getPointKey(),
                useTransaction.getPointKey(),
                userId,
                useTransaction.getOrderId(),
                cancelAmount,
                cancelable - cancelAmount,
                lotRepository.sumAvailableAmount(userId, PointLotStatus.AVAILABLE, now),
                details);
    }

    private CanceledLot restoreOrReissue(PointLotUsage usage,
                                         long amount,
                                         PointTransaction cancelTransaction,
                                         PointPolicy policy,
                                         LocalDateTime now) {
        PointLot originLot = lotRepository.findById(usage.getLotId())
                .orElseThrow(() -> PointException.of(ErrorCode.LOT_NOT_FOUND, "lotId=" + usage.getLotId()));

        if (originLot.isRestorableAt(now)) {
            originLot.restore(amount);
            lotUsageCancelRepository.save(PointLotUsageCancel.restored(
                    cancelTransaction.getId(), usage.getId(), amount, originLot.getId(), now));
            return new CanceledLot(
                    earnPointKeyOf(originLot), amount, false, null, originLot.getExpireAt());
        }

        PointTransaction reissuedTransaction = transactionRepository.save(PointTransaction.reissuedEarn(
                originLot.getUserId(), amount, cancelTransaction.getId(), REISSUE_MEMO, now));
        PointLot reissuedLot = lotRepository.save(PointLot.create(
                reissuedTransaction.getId(),
                originLot.getUserId(),
                amount,
                originLot.isManual(),
                now.plusDays(policy.getDefaultExpireDays()),
                now));
        lotUsageCancelRepository.save(PointLotUsageCancel.reissued(
                cancelTransaction.getId(), usage.getId(), amount, reissuedLot.getId(), now));

        return new CanceledLot(
                earnPointKeyOf(originLot), amount, true, reissuedTransaction.getPointKey(), reissuedLot.getExpireAt());
    }

    private String earnPointKeyOf(PointLot lot) {
        return transactionRepository.findById(lot.getTransactionId())
                .map(PointTransaction::getPointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "transactionId=" + lot.getTransactionId()));
    }
}
