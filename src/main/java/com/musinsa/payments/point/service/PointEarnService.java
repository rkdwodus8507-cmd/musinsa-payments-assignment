package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.service.dto.CancelEarnCommand;
import com.musinsa.payments.point.service.dto.EarnCancelResult;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointEarnService {

    private final PointTransactionRepository transactionRepository;
    private final EarnedPointRepository earnedPointRepository;
    private final EarnedPointReader earnedPointReader;
    private final PointPolicyService policyService;
    private final UserPointLocker userPointLocker;
    private final PointIdempotencyGuard idempotencyGuard;
    private final Clock clock;

    @Transactional
    public EarnResult earn(EarnCommand command) {
        userPointLocker.lock(command.userId());

        return idempotencyGuard.findHandled(command.userId(), command.requestKey(), PointTransactionType.EARN)
                .map(this::toEarnResult)
                .orElseGet(() -> grantPoints(command));
    }

    @Transactional
    public EarnCancelResult cancelEarn(CancelEarnCommand command) {
        PointTransaction earnTransaction = findEarnTransaction(command.earnPointKey());
        userPointLocker.lock(earnTransaction.getUserId());

        return idempotencyGuard.findHandled(earnTransaction.getUserId(), command.requestKey(), PointTransactionType.EARN_CANCEL)
                .map(this::toEarnCancelResult)
                .orElseGet(() -> takeBackPoints(earnTransaction, command.requestKey()));
    }

    private EarnResult grantPoints(EarnCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointPolicy policy = policyService.getPolicy();

        policy.validateEarnAmount(command.amount());
        policy.validateBalanceAfterEarn(earnedPointReader.balanceOf(command.userId()), command.amount());
        LocalDateTime expireAt = now.plusDays(policy.resolveExpireDays(command.expireDays()));

        PointTransaction transaction = transactionRepository.save(PointTransaction.earn(
                command.userId(), command.amount(), command.memo(), command.requestKey(), now));
        earnedPointRepository.save(EarnedPoint.of(
                transaction.getId(), command.userId(), command.amount(), command.manual(), expireAt, now));

        return toEarnResult(transaction);
    }

    private EarnCancelResult takeBackPoints(PointTransaction earnTransaction, String requestKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        EarnedPoint earnedPoint = earnedPointReader.byTransaction(earnTransaction.getId());
        earnedPoint.cancel(now);

        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.earnCancel(
                earnTransaction.getUserId(), earnedPoint.getOriginalAmount(), earnTransaction.getId(), requestKey, now));

        return toEarnCancelResult(cancelTransaction);
    }

    private EarnResult toEarnResult(PointTransaction earnTransaction) {
        EarnedPoint earnedPoint = earnedPointReader.byTransaction(earnTransaction.getId());
        return new EarnResult(
                earnTransaction.getPointKey(),
                earnTransaction.getUserId(),
                earnTransaction.getAmount(),
                earnedPoint.isManual(),
                earnedPoint.getExpireAt(),
                earnedPointReader.balanceOf(earnTransaction.getUserId()));
    }

    private EarnCancelResult toEarnCancelResult(PointTransaction cancelTransaction) {
        PointTransaction earnTransaction = findTransaction(cancelTransaction.getRelatedTransactionId());
        return new EarnCancelResult(
                cancelTransaction.getPointKey(),
                earnTransaction.getPointKey(),
                cancelTransaction.getUserId(),
                cancelTransaction.getAmount(),
                earnedPointReader.balanceOf(cancelTransaction.getUserId()));
    }

    private PointTransaction findEarnTransaction(String pointKey) {
        PointTransaction transaction = transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
        if (!transaction.isEarn()) {
            throw PointException.of(ErrorCode.NOT_EARN_TRANSACTION, "type=" + transaction.getType());
        }
        return transaction;
    }

    private PointTransaction findTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "transactionId=" + transactionId));
    }
}
