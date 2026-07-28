package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointLot;
import com.musinsa.payments.point.domain.PointLotStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointLotRepository extends JpaRepository<PointLot, Long> {

    Optional<PointLot> findByTransactionId(Long transactionId);

    @Query("""
            select coalesce(sum(l.remainingAmount), 0)
            from PointLot l
            where l.userId = :userId
              and l.status = :status
              and l.expireAt > :now
            """)
    long sumAvailableAmount(@Param("userId") Long userId,
                            @Param("status") PointLotStatus status,
                            @Param("now") LocalDateTime now);

    @Query("""
            select l
            from PointLot l
            where l.userId = :userId
              and l.status = :status
              and l.expireAt > :now
              and l.remainingAmount > 0
            order by l.manual desc, l.expireAt asc, l.id asc
            """)
    List<PointLot> findUsableLots(@Param("userId") Long userId,
                                  @Param("status") PointLotStatus status,
                                  @Param("now") LocalDateTime now);

    @Query("""
            select l
            from PointLot l
            where l.status = :status
              and l.expireAt <= :now
            order by l.id asc
            """)
    List<PointLot> findExpirationTargets(@Param("status") PointLotStatus status,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    List<PointLot> findByUserIdOrderByIdAsc(Long userId);
}
