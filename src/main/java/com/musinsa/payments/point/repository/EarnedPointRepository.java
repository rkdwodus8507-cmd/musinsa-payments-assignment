package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.EarnedPoint;
import com.musinsa.payments.point.domain.EarnedPointStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EarnedPointRepository extends JpaRepository<EarnedPoint, Long> {

    Optional<EarnedPoint> findByTransactionId(Long transactionId);

    List<EarnedPoint> findByUserIdOrderByIdAsc(Long userId);

    @Query("""
            select coalesce(sum(e.remainingAmount), 0)
            from EarnedPoint e
            where e.userId = :userId
              and e.status = :status
              and e.expireAt > :now
            """)
    long sumAvailableAmount(@Param("userId") Long userId,
                            @Param("status") EarnedPointStatus status,
                            @Param("now") LocalDateTime now);

    @Query("""
            select e
            from EarnedPoint e
            where e.userId = :userId
              and e.status = :status
              and e.expireAt > :now
              and e.remainingAmount > 0
            order by e.manual desc, e.expireAt asc, e.id asc
            """)
    List<EarnedPoint> findUsableInPriorityOrder(@Param("userId") Long userId,
                                                @Param("status") EarnedPointStatus status,
                                                @Param("now") LocalDateTime now);

    @Query("""
            select e
            from EarnedPoint e
            where e.status = :status
              and e.expireAt <= :now
            order by e.id asc
            """)
    List<EarnedPoint> findExpirationTargets(@Param("status") EarnedPointStatus status,
                                            @Param("now") LocalDateTime now,
                                            Pageable pageable);
}
