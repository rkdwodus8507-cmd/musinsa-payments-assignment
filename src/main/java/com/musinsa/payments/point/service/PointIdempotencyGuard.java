package com.musinsa.payments.point.service;

import com.musinsa.payments.point.domain.PointTransaction;
import com.musinsa.payments.point.domain.PointTransactionType;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointIdempotencyGuard {

    private final PointTransactionRepository transactionRepository;
    private final PointAuditRecorder auditRecorder;

    public <T> T runOnce(Long userId,
                         String requestKey,
                         PointTransactionType type,
                         Function<PointTransaction, T> whenAlreadyHandled,
                         Supplier<T> whenFirstRequest) {
        Optional<PointTransaction> handled = findHandled(userId, requestKey, type);
        if (handled.isPresent()) {
            auditRecorder.recordDuplicate(handled.get());
            return whenAlreadyHandled.apply(handled.get());
        }
        return whenFirstRequest.get();
    }

    private Optional<PointTransaction> findHandled(Long userId, String requestKey, PointTransactionType type) {
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
