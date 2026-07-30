package com.musinsa.payments.point.repository;

import com.musinsa.payments.point.repository.dialect.H2UserPointLockRegistrar;
import com.musinsa.payments.point.service.UserPointLockRegistrar;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용자 락 행 생성 - DB 문법 차이를 가두는 지점")
class UserPointLockRegistrarTest extends IntegrationTestSupport {

    @Autowired
    private UserPointLockRegistrar lockRegistrar;

    @Test
    @DisplayName("기본 설정에서는 H2 구현이 선택된다")
    void h2ImplementationIsSelectedByDefault() {
        assertThat(lockRegistrar)
                .as("dialect 별 구현을 갈아끼우는 자리다. MySQL 로 옮기면 point.database.dialect=mysql 로 바꾼다")
                .isInstanceOf(H2UserPointLockRegistrar.class);
    }

    @Test
    @DisplayName("같은 사용자로 여러 번 호출해도 행은 하나만 남는다")
    void registerIsIdempotent() {
        LocalDateTime now = LocalDateTime.now(clock);

        lockRegistrar.registerIfAbsent(999L, now);
        lockRegistrar.registerIfAbsent(999L, now);
        lockRegistrar.registerIfAbsent(999L, now);

        assertThat(lockRepository.findByUserId(999L)).isPresent();
        assertThat(lockRepository.findAll()).filteredOn(it -> it.getUserId().equals(999L)).hasSize(1);
    }
}
