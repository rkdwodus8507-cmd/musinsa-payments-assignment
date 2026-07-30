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
import java.util.Optional;
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
    private final PointTransactionReader transactionReader;
    private final PointIdempotencyGuard idempotencyGuard;
    private final Clock clock;

    @Transactional
    public EarnResult earn(EarnCommand command) {
        userPointLocker.lock(command.userId());

        Optional<PointTransaction> alreadyEarned =
                idempotencyGuard.findHandled(command.userId(), command.requestKey(), PointTransactionType.EARN);
        if (alreadyEarned.isPresent()) {
            return toEarnResult(alreadyEarned.get());
        }
        return grantPoints(command);
    }

    @Transactional
    public EarnCancelResult cancelEarn(CancelEarnCommand command) {
        PointTransaction earnTransaction = transactionReader.earnByPointKey(command.earnPointKey());
        userPointLocker.lock(earnTransaction.getUserId());

        Optional<PointTransaction> alreadyCanceled = idempotencyGuard.findHandled(
                earnTransaction.getUserId(), command.requestKey(), PointTransactionType.EARN_CANCEL);
        if (alreadyCanceled.isPresent()) {
            return toEarnCancelResult(alreadyCanceled.get());
        }
        return takeBackPoints(earnTransaction, command.requestKey());
    }

    private EarnResult grantPoints(EarnCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointPolicy policy = policyService.getPolicy();

        policy.validateEarnAmount(command.amount());
        policy.validateBalanceAfterEarn(earnedPointReader.balanceOf(command.userId()), command.amount());
        LocalDateTime expireAt = now.plusDays(policy.resolveExpireDays(command.expireDays()));

        PointTransaction earnTransaction = transactionRepository.save(PointTransaction.earn(
                command.userId(), command.amount(), command.memo(), command.requestKey(), now));
        earnedPointRepository.save(EarnedPoint.from(earnTransaction, command.manual(), expireAt, now));

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
