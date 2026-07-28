package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import com.musinsa.payments.point.domain.PointLotUsage;
import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.repository.PointLotRepository;
import com.musinsa.payments.point.repository.PointLotUsageRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.service.dto.PointResults;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointQueryService {

    private final PointTransactionRepository transactionRepository;
    private final PointLotRepository lotRepository;
    private final PointLotUsageRepository lotUsageRepository;
    private final Clock clock;

    public PointResults.Balance getBalance(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PointLot> lots = lotRepository.findByUserIdOrderByIdAsc(userId);
        Map<Long, String> pointKeys = pointKeysOf(lots);

        long balance = 0;
        long manualBalance = 0;
        List<PointResults.Lot> lotResults = new ArrayList<>();
        for (PointLot lot : lots) {
            boolean available = lot.isUsableAt(now);
            if (available) {
                balance += lot.getRemainingAmount();
                if (lot.isManual()) {
                    manualBalance += lot.getRemainingAmount();
                }
            }
            lotResults.add(new PointResults.Lot(
                    pointKeys.get(lot.getTransactionId()),
                    lot.getOriginalAmount(),
                    lot.getRemainingAmount(),
                    lot.isManual(),
                    displayStatus(lot, now),
                    lot.getExpireAt()));
        }
        return new PointResults.Balance(userId, balance, manualBalance, lotResults);
    }

    public Page<PointResults.Transaction> getTransactions(Long userId, Pageable pageable) {
        return transactionRepository.findByUserIdOrderByIdDesc(userId, pageable)
                .map(transaction -> new PointResults.Transaction(
                        transaction.getPointKey(),
                        transaction.getType().name(),
                        transaction.getAmount(),
                        transaction.getOrderId(),
                        transaction.getMemo(),
                        transaction.getCreatedAt()));
    }

    public PointResults.OrderUsage getOrderUsage(String orderId) {
        List<PointLotUsage> usages = lotUsageRepository.findByOrderIdOrderByIdAsc(orderId);
        Map<Long, PointLot> lots = lotRepository.findAllById(
                        usages.stream().map(PointLotUsage::getLotId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PointLot::getId, Function.identity()));
        Map<Long, String> usePointKeys = transactionRepository.findByIdIn(
                        usages.stream().map(PointLotUsage::getUseTransactionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PointTransaction::getId, PointTransaction::getPointKey));
        Map<Long, String> earnPointKeys = pointKeysOf(lots.values());

        List<PointResults.OrderUsageDetail> details = usages.stream()
                .map(usage -> {
                    PointLot lot = lots.get(usage.getLotId());
                    return new PointResults.OrderUsageDetail(
                            usePointKeys.get(usage.getUseTransactionId()),
                            earnPointKeys.get(lot.getTransactionId()),
                            usage.getAmount(),
                            usage.getCanceledAmount(),
                            lot.isManual(),
                            lot.getExpireAt());
                })
                .toList();

        long usedAmount = usages.stream().mapToLong(PointLotUsage::getAmount).sum();
        long canceledAmount = usages.stream().mapToLong(PointLotUsage::getCanceledAmount).sum();
        return new PointResults.OrderUsage(orderId, usedAmount, canceledAmount, details);
    }

    private Map<Long, String> pointKeysOf(Iterable<PointLot> lots) {
        List<Long> transactionIds = new ArrayList<>();
        lots.forEach(lot -> transactionIds.add(lot.getTransactionId()));
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return transactionRepository.findByIdIn(transactionIds).stream()
                .collect(Collectors.toMap(PointTransaction::getId, PointTransaction::getPointKey));
    }

    private String displayStatus(PointLot lot, LocalDateTime now) {
        if (lot.getStatus() == PointLotStatus.AVAILABLE && !lot.getExpireAt().isAfter(now)) {
            return PointLotStatus.EXPIRED.name();
        }
        return lot.getStatus().name();
    }
}
