package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointLotUsageCancel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointLotUsageCancelRepository extends JpaRepository<PointLotUsageCancel, Long> {

    List<PointLotUsageCancel> findByCancelTransactionIdOrderByIdAsc(Long cancelTransactionId);

    List<PointLotUsageCancel> findByLotUsageIdOrderByIdAsc(Long lotUsageId);
}
