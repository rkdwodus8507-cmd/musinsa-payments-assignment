package com.musinsa.payments.point.api;

import com.musinsa.payments.point.api.dto.EarnRequest;
import com.musinsa.payments.point.api.dto.UpdatePolicyRequest;
import com.musinsa.payments.point.config.PointExpirationProperties;
import com.musinsa.payments.point.service.PointEarnService;
import com.musinsa.payments.point.service.PointExpirationService;
import com.musinsa.payments.point.service.PointPolicyService;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.ExpirationResult;
import com.musinsa.payments.point.service.dto.PolicyResult;
import com.musinsa.payments.point.service.dto.UpdatePolicyCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트 관리자", description = "수기지급 / 정책 변경 / 만료 배치 수동 실행")
@RestController
@RequestMapping("/api/v1/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointEarnService earnService;
    private final PointPolicyService policyService;
    private final PointExpirationService expirationService;
    private final PointExpirationProperties expirationProperties;

    @Operation(summary = "관리자 수기 적립", description = "수기지급 포인트는 사용 시 우선 차감된다.")
    @PostMapping("/earn")
    public EarnResult manualEarn(@Valid @RequestBody EarnRequest request) {
        return earnService.earn(EarnCommand.ofAdmin(
                request.userId(), request.amount(), request.expireDays(), request.memo()));
    }

    @Operation(summary = "포인트 정책 조회")
    @GetMapping("/policies")
    public PolicyResult getPolicy() {
        return policyService.findPolicy();
    }

    @Operation(summary = "포인트 정책 변경", description = "1회 최대 적립금액, 개인 최대 보유금액, 만료일 범위를 무중단으로 변경한다.")
    @PutMapping("/policies")
    public PolicyResult updatePolicy(@Valid @RequestBody UpdatePolicyRequest request) {
        return policyService.updatePolicy(new UpdatePolicyCommand(
                request.minEarnAmount(),
                request.maxEarnAmount(),
                request.maxUserBalance(),
                request.defaultExpireDays(),
                request.minExpireDays(),
                request.maxExpireDays()));
    }

    @Operation(summary = "만료 배치 수동 실행")
    @PostMapping("/expirations")
    public ExpirationResult expire() {
        return expirationService.expireAll(expirationProperties.chunkSize());
    }
}
