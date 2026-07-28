package com.musinsa.payments.point.support;

import com.musinsa.payments.point.repository.PointLotRepository;
import com.musinsa.payments.point.repository.PointLotUsageCancelRepository;
import com.musinsa.payments.point.repository.PointLotUsageRepository;
import com.musinsa.payments.point.repository.PointPolicyRepository;
import com.musinsa.payments.point.repository.PointTransactionRepository;
import com.musinsa.payments.point.repository.PointWalletRepository;
import com.musinsa.payments.point.service.PointEarnService;
import com.musinsa.payments.point.service.PointExpirationService;
import com.musinsa.payments.point.service.PointPolicyService;
import com.musinsa.payments.point.service.PointQueryService;
import com.musinsa.payments.point.service.PointUseService;
import com.musinsa.payments.point.service.dto.PointCommands;
import com.musinsa.payments.point.service.dto.PointResults;
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
    protected PointWalletRepository walletRepository;

    @Autowired
    protected PointTransactionRepository transactionRepository;

    @Autowired
    protected PointLotRepository lotRepository;

    @Autowired
    protected PointLotUsageRepository lotUsageRepository;

    @Autowired
    protected PointLotUsageCancelRepository lotUsageCancelRepository;

    @Autowired
    protected PointPolicyRepository policyRepository;

    @BeforeEach
    void resetState() {
        lotUsageCancelRepository.deleteAllInBatch();
        lotUsageRepository.deleteAllInBatch();
        lotRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        policyRepository.deleteAllInBatch();
        clock.reset();
        policyService.initializeIfAbsent();
    }

    protected PointResults.Earn earn(long amount, Integer expireDays) {
        return earnService.earn(PointCommands.Earn.ofUser(USER_ID, amount, expireDays, null));
    }

    protected PointResults.Earn manualEarn(long amount, Integer expireDays) {
        return earnService.earn(PointCommands.Earn.ofAdmin(USER_ID, amount, expireDays, "수기지급"));
    }

    protected PointResults.Use use(String orderId, long amount) {
        return useService.use(new PointCommands.Use(USER_ID, orderId, amount));
    }

    protected long balanceOf(long userId) {
        return queryService.getBalance(userId).balance();
    }
}
