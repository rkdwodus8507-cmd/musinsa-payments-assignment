package com.musinsa.payments.point.support;

import com.musinsa.payments.point.repository.EarnedPointRepository;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.repository.PointUsageCancellationRepository;
import com.musinsa.payments.point.repository.PointUsageRepository;
import com.musinsa.payments.point.repository.UserPointLockRepository;
import com.musinsa.payments.point.service.PointEarnService;
import com.musinsa.payments.point.service.PointExpirationService;
import com.musinsa.payments.point.service.PointPolicyService;
import com.musinsa.payments.point.service.PointQueryService;
import com.musinsa.payments.point.service.PointUseService;
import com.musinsa.payments.point.service.dto.CancelEarnCommand;
import com.musinsa.payments.point.service.dto.CancelUseCommand;
import com.musinsa.payments.point.service.dto.EarnCancelResult;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.service.dto.EarnResult;
import com.musinsa.payments.point.service.dto.UseCancelResult;
import com.musinsa.payments.point.service.dto.UseCommand;
import com.musinsa.payments.point.service.dto.UseResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestClockConfig.class)
public abstract class IntegrationTestSupport {

    protected static final long USER_ID = 1L;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected PointEarnService earnService;

    @Autowired
    protected PointUseService useService;

    @Autowired
    protected PointQueryService queryService;

    @Autowired
    protected PointPolicyService policyService;

    @Autowired
    protected PointExpirationService expirationService;

    @Autowired
    protected UserPointLockRepository lockRepository;

    @Autowired
    protected PointTransactionRepository transactionRepository;

    @Autowired
    protected EarnedPointRepository earnedPointRepository;

    @Autowired
    protected PointUsageRepository usageRepository;

    @Autowired
    protected PointUsageCancellationRepository cancellationRepository;

    @Autowired
    protected PointPolicyRepository policyRepository;

    @BeforeEach
    void resetState() {
        cancellationRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        earnedPointRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        lockRepository.deleteAllInBatch();
        policyRepository.deleteAllInBatch();
        clock.reset();
        policyService.initializeIfAbsent();
    }

    protected EarnResult earn(long amount, Integer expireDays) {
        return earnService.earn(EarnCommand.ofUser(USER_ID, amount, expireDays, null, null));
    }

    protected EarnResult manualEarn(long amount, Integer expireDays) {
        return earnService.earn(EarnCommand.ofAdmin(USER_ID, amount, expireDays, "수기지급", null));
    }

    protected UseResult use(String orderId, long amount) {
        return useService.use(new UseCommand(USER_ID, orderId, amount, null));
    }

    protected EarnCancelResult cancelEarn(String earnPointKey) {
        return earnService.cancelEarn(new CancelEarnCommand(earnPointKey, null));
    }

    protected UseCancelResult cancelUse(String usePointKey, long amount) {
        return useService.cancelUse(new CancelUseCommand(usePointKey, amount, null));
    }

    protected long balanceOf(long userId) {
        return queryService.getBalance(userId).balance();
    }
}
