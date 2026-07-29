package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointTransaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByPointKey(String pointKey);

    Optional<PointTransaction> findByUserIdAndRequestKey(Long userId, String requestKey);

    Page<PointTransaction> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<PointTransaction> findByIdIn(Collection<Long> ids);
}
