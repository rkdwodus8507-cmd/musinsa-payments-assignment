package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointUsage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {

    List<PointUsage> findByUseTransactionIdOrderByIdAsc(Long useTransactionId);

    List<PointUsage> findByOrderIdOrderByIdAsc(String orderId);

    List<PointUsage> findByIdIn(Collection<Long> ids);
}
