package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointLotUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointLotUsageRepository extends JpaRepository<PointLotUsage, Long> {

    List<PointLotUsage> findByUseTransactionIdOrderByIdAsc(Long useTransactionId);

    List<PointLotUsage> findByOrderIdOrderByIdAsc(String orderId);

    List<PointLotUsage> findByLotIdOrderByIdAsc(Long lotId);
}
