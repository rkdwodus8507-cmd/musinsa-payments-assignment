package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointUsageCancellation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageCancellationRepository extends JpaRepository<PointUsageCancellation, Long> {

    List<PointUsageCancellation> findByCancelTransactionIdOrderByIdAsc(Long cancelTransactionId);
}
