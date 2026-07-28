package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.domain.PointPolicy;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.repository.PointLotRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.service.dto.PointCommands;
import com.musinsa.payments.point.service.dto.PointResults;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PointEarnService {

    private final PointTransactionRepository transactionRepository;
    private final PointLotRepository lotRepository;
    private final PointPolicyService policyService;
    private final PointWalletLocker walletLocker;
    private final Clock clock;

    @Transactional
    public PointResults.Earn earn(PointCommands.Earn command) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointPolicy policy = policyService.getPolicy();
        policy.validateEarnAmount(command.amount());
        int expireDays = policy.resolveExpireDays(command.expireDays());

        walletLocker.lock(command.userId());

        long balance = availableBalance(command.userId(), now);
        policy.validateBalanceAfterEarn(balance, command.amount());

        PointTransaction transaction = transactionRepository.save(
                PointTransaction.earn(command.userId(), command.amount(), command.memo(), now));
        PointLot lot = lotRepository.save(PointLot.create(
                transaction.getId(),
                command.userId(),
                command.amount(),
                command.manual(),
                now.plusDays(expireDays),
                now));

        return new PointResults.Earn(
                transaction.getPointKey(),
                command.userId(),
                command.amount(),
                lot.isManual(),
                lot.getExpireAt(),
                balance + command.amount());
    }

    @Transactional
    public PointResults.EarnCancel cancelEarn(String pointKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointTransaction earnTransaction = transactionRepository.findByPointKey(pointKey)
                .orElseThrow(() -> PointException.of(ErrorCode.TRANSACTION_NOT_FOUND, "pointKey=" + pointKey));
        if (!earnTransaction.isEarn()) {
            throw PointException.of(ErrorCode.NOT_EARN_TRANSACTION, "type=" + earnTransaction.getType());
        }

        walletLocker.lock(earnTransaction.getUserId());

        PointLot lot = lotRepository.findByTransactionId(earnTransaction.getId())
                .orElseThrow(() -> PointException.of(ErrorCode.LOT_NOT_FOUND, "transactionId=" + earnTransaction.getId()));
        long canceledAmount = lot.getOriginalAmount();
        lot.cancelEarn(now);

        PointTransaction cancelTransaction = transactionRepository.save(
                PointTransaction.earnCancel(earnTransaction.getUserId(), canceledAmount, earnTransaction.getId(), now));

        return new PointResults.EarnCancel(
                cancelTransaction.getPointKey(),
                earnTransaction.getPointKey(),
                earnTransaction.getUserId(),
                canceledAmount,
                availableBalance(earnTransaction.getUserId(), now));
    }

    private long availableBalance(Long userId, LocalDateTime now) {
        return lotRepository.sumAvailableAmount(userId, PointLotStatus.AVAILABLE, now);
    }
}
