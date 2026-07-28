package com.musinsa.payments.point.service;

import com.musinsa.payments.point.service.dto.PointCommands;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("동시성 - 사용자 단위 비관적 락")
class PointConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 10;

    private final java.util.List<String> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @Test
    @DisplayName("잔액 500 상태에서 100포인트 사용을 10건 동시 요청하면 정확히 5건만 성공한다")
    void concurrentUseDoesNotOverspend() throws InterruptedException {
        earn(500, null);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        runConcurrently(index -> useService.use(new PointCommands.Use(USER_ID, "ORDER-" + index, 100)),
                success, failure);

        assertThat(success.get()).isEqualTo(5);
        assertThat(failure.get()).isEqualTo(5);
        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(lotRepository.findByUserIdOrderByIdAsc(USER_ID))
                .allSatisfy(lot -> assertThat(lot.getRemainingAmount()).isNotNegative());
    }

    @Test
    @DisplayName("최대 보유금액 근처에서 동시 적립해도 한도를 넘지 않는다")
    void concurrentEarnDoesNotExceedMaxBalance() throws InterruptedException {
        earn(100_000, null);
        policyService.updatePolicy(new PointCommands.UpdatePolicy(
                1, 100_000, 500_000, 365, 1, 1824));

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        runConcurrently(index -> earnService.earn(PointCommands.Earn.ofUser(USER_ID, 100_000, null, null)),
                success, failure);

        assertThat(success.get()).isEqualTo(4);
        assertThat(balanceOf(USER_ID)).isEqualTo(500_000);
    }

    @Test
    @DisplayName("최초 요청이 동시에 들어와도 지갑은 하나만 생성된다")
    void concurrentFirstRequestCreatesSingleWallet() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        runConcurrently(index -> earnService.earn(PointCommands.Earn.ofUser(99L, 10, null, null)),
                success, failure);

        assertThat(success.get()).as("실패 원인: %s", errors).isEqualTo(THREAD_COUNT);
        assertThat(walletRepository.findByUserId(99L)).isPresent();
        assertThat(balanceOf(99L)).isEqualTo(100);
    }

    private void runConcurrently(java.util.function.IntConsumer task,
                                 AtomicInteger success,
                                 AtomicInteger failure) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(index);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }
}
