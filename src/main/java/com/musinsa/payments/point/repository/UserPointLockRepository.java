package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.UserPointLock;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointLockRepository extends JpaRepository<UserPointLock, Long> {

    Optional<UserPointLock> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from UserPointLock l where l.userId = :userId")
    Optional<UserPointLock> findByUserIdForUpdate(@Param("userId") Long userId);

    @Modifying
    @Query(value = "merge into user_point_lock (user_id, created_at) key (user_id) values (:userId, :createdAt)",
            nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("createdAt") LocalDateTime createdAt);
}
