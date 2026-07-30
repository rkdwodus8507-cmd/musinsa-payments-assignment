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
    private final PointPolicyReader policyReader;
    private final UserPointLocker userPointLocker;
    private final PointTransactionReader transactionReader;
    private final PointIdempotencyGuard idempotencyGuard;
    private final Clock clock;

    @Transactional
    public EarnResult earn(EarnCommand command) {
        userPointLocker.lock(command.getUserId());

        return idempotencyGuard.runOnce(command.getUserId(), command.getRequestKey(), PointTransactionType.EARN,
                this::toEarnResult,
                () -> grantPoints(command));
    }

    @Transactional
    public EarnCancelResult cancelEarn(CancelEarnCommand command) {
        PointTransaction earnTransaction = transactionReader.earnByPointKey(command.getEarnPointKey());
        userPointLocker.lock(earnTransaction.getUserId());

        return idempotencyGuard.runOnce(earnTransaction.getUserId(), command.getRequestKey(), PointTransactionType.EARN_CANCEL,
                this::toEarnCancelResult,
                () -> takeBackPoints(earnTransaction, command.getRequestKey()));
    }

    private EarnResult grantPoints(EarnCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointPolicy policy = policyReader.current();

        policy.validateEarnAmount(command.getAmount());
        policy.validateBalanceAfterEarn(earnedPointReader.balanceOf(command.getUserId()), command.getAmount());
        LocalDateTime expireAt = now.plusDays(policy.resolveExpireDays(command.getExpireDays()));

        PointTransaction earnTransaction = transactionRepository.save(PointTransaction.earn(
                command.getUserId(), command.getAmount(), command.getMemo(), command.getRequestKey(), now));
        earnedPointRepository.save(EarnedPoint.from(earnTransaction, command.isManual(), expireAt, now));

        return toEarnResult(earnTransaction);
    }

    private EarnCancelResult takeBackPoints(PointTransaction earnTransaction, String requestKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        EarnedPoint earnedPoint = earnedPointReader.byTransaction(earnTransaction.getId());
        earnedPoint.cancel(now);

        PointTransaction cancelTransaction = transactionRepository.save(PointTransaction.earnCancel(
                earnTransaction, earnedPoint.getOriginalAmount(), requestKey, now));

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
        PointTransaction earnTransaction = transactionReader.byId(cancelTransaction.getRelatedTransactionId());
        return new EarnCancelResult(
                cancelTransaction.getPointKey(),
                earnTransaction.getPointKey(),
                cancelTransaction.getUserId(),
                cancelTransaction.getAmount(),
                earnedPointReader.balanceOf(cancelTransaction.getUserId()));
    }

}
