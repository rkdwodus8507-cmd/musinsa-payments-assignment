package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.domain.PointPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {
}
