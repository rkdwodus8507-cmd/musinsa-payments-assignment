package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointIdempotencyGuard {

    private final PointTransactionRepository transactionRepository;

    public Optional<PointTransaction> findHandled(Long userId, String requestKey, PointTransactionType type) {
        if (requestKey == null || requestKey.isBlank()) {
            return Optional.empty();
        }
        return transactionRepository.findByUserIdAndRequestKey(userId, requestKey)
                .map(handled -> verifySameOperation(handled, requestKey, type));
    }

    private PointTransaction verifySameOperation(PointTransaction handled, String requestKey, PointTransactionType type) {
        if (handled.getType() != type) {
            throw PointException.of(ErrorCode.REQUEST_KEY_CONFLICT,
                    "requestKey=%s, 기존=%s, 요청=%s".formatted(requestKey, handled.getType(), type));
        }
        return handled;
    }
}
