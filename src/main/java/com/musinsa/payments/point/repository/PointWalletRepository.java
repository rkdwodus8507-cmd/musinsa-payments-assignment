package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    Optional<PointWallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PointWallet w where w.userId = :userId")
    Optional<PointWallet> findByUserIdForUpdate(@Param("userId") Long userId);

    @Modifying
    @Query(value = "merge into point_wallet (user_id, created_at) key (user_id) values (:userId, :createdAt)",
            nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("createdAt") LocalDateTime createdAt);
}
