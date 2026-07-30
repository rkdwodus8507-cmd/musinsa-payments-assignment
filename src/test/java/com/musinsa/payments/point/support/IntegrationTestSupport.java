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
import com.musinsa.payments.point.support.error.ErrorCode;
import com.musinsa.payments.point.support.error.PointException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class IntegrationTestSupport {

    protected static final long USER_ID = 1L;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    protected void resetState() {
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

    protected Statistics startCountingQueries() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    protected long balanceOf(long userId) {
        return queryService.getBalance(userId).getBalance();
    }

    protected <T> ConcurrentRun<T> runConcurrently(int threadCount, IntFunction<T> task) {
        List<T> successes = Collections.synchronizedList(new java.util.ArrayList<>());
        List<String> failures = Collections.synchronizedList(new java.util.ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    successes.add(task.apply(index));
                } catch (Exception e) {
                    failures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        try {
            done.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
        return new ConcurrentRun<>(successes, failures);
    }

    protected static class ConcurrentRun<T> {

        private final List<T> successes;
        private final List<String> failures;

        ConcurrentRun(List<T> successes, List<String> failures) {
            this.successes = successes;
            this.failures = failures;
        }

        public List<T> successes() {
            return successes;
        }

        public List<String> failures() {
            return failures;
        }

        public int successCount() {
            return successes.size();
        }

        public int failureCount() {
            return failures.size();
        }
    }
}
