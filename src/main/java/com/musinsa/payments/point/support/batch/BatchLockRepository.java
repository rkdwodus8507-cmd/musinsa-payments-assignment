package com.musinsa.payments.point.support.batch;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BatchLockRepository extends JpaRepository<BatchLock, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BatchLock l
            set l.holder = :holder, l.acquiredAt = :now, l.expiresAt = :expiresAt
            where l.name = :name and l.expiresAt <= :now
            """)
    int acquire(@Param("name") String name,
                @Param("holder") String holder,
                @Param("now") LocalDateTime now,
                @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BatchLock l
            set l.expiresAt = :releasedAt
            where l.name = :name and l.holder = :holder
            """)
    int release(@Param("name") String name,
                @Param("holder") String holder,
                @Param("releasedAt") LocalDateTime releasedAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BatchLock l set l.holder = 'none', l.expiresAt = :releasedAt")
    int releaseAll(@Param("releasedAt") LocalDateTime releasedAt);
}
