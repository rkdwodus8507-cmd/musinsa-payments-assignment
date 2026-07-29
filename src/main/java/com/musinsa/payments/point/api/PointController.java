package com.musinsa.payments.point.api;

import com.musinsa.payments.point.api.dto.CancelEarnRequest;
import com.musinsa.payments.point.api.dto.CancelUseRequest;
import com.musinsa.payments.point.api.dto.EarnRequest;
import com.musinsa.payments.point.api.dto.UseRequest;
import com.musinsa.payments.point.service.PointEarnService;
import com.musinsa.payments.point.service.PointQueryService;
import com.musinsa.payments.point.service.PointUseService;
import com.musinsa.payments.point.service.dto.BalanceResult;
import com.musinsa.payments.point.service.dto.CancelEarnCommand;
import com.musinsa.payments.point.service.dto.CancelUseCommand;
import com.musinsa.payments.point.service.dto.EarnCancelResult;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.OrderUsageResult;
import com.musinsa.payments.point.service.dto.TransactionResult;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.service.dto.UseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "무료 포인트 적립 / 적립취소 / 사용 / 사용취소")
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointEarnService earnService;
    private final PointUseService useService;
    private final PointQueryService queryService;

    @Operation(summary = "포인트 적립", description = "requestKey 를 보내면 같은 요청이 재전송되어도 한 번만 적립된다.")
    @PostMapping("/earn")
    public EarnResult earn(@Valid @RequestBody EarnRequest request) {
        return earnService.earn(EarnCommand.ofUser(
                request.userId(), request.amount(), request.expireDays(), request.memo(), request.requestKey()));
    }

    @Operation(summary = "포인트 적립 취소", description = "적립분이 일부라도 사용되었으면 취소할 수 없다.")
    @PostMapping("/earn/{pointKey}/cancel")
    public EarnCancelResult cancelEarn(@PathVariable String pointKey,
                                       @RequestBody(required = false) CancelEarnRequest request) {
        String requestKey = request == null ? null : request.requestKey();
        return earnService.cancelEarn(new CancelEarnCommand(pointKey, requestKey));
    }

    @Operation(summary = "포인트 사용", description = "관리자 수기지급분 우선, 만료 임박 순으로 차감한다.")
    @PostMapping("/use")
    public UseResult use(@Valid @RequestBody UseRequest request) {
        return useService.use(new UseCommand(
                request.userId(), request.orderId(), request.amount(), request.requestKey()));
    }

    @Operation(summary = "포인트 사용 취소", description = "전체 또는 일부 취소 가능. 복원 대상이 만료된 경우 신규 적립으로 처리한다.")
    @PostMapping("/use/{pointKey}/cancel")
    public UseCancelResult cancelUse(@PathVariable String pointKey,
                                     @Valid @RequestBody CancelUseRequest request) {
        return useService.cancelUse(new CancelUseCommand(pointKey, request.amount(), request.requestKey()));
    }

    @Operation(summary = "포인트 잔액 및 적립분 조회")
    @GetMapping("/balance")
    public BalanceResult getBalance(@RequestParam Long userId) {
        return queryService.getBalance(userId);
    }

    @Operation(summary = "포인트 거래 이력 조회")
    @GetMapping("/transactions")
    public Page<TransactionResult> getTransactions(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return queryService.getTransactions(userId, PageRequest.of(page, size));
    }

    @Operation(summary = "주문별 포인트 사용 추적", description = "해당 주문에서 어떤 적립분이 1원 단위로 얼마 사용되었는지 조회한다.")
    @GetMapping("/orders/{orderId}/usages")
    public OrderUsageResult getOrderUsage(@PathVariable String orderId) {
        return queryService.getOrderUsage(orderId);
    }
}
