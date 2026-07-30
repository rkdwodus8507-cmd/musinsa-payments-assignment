package com.musinsa.payments.point.repository.dialect;

import com.musinsa.payments.point.service.UserPointLockRegistrar;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "point.database", name = "dialect", havingValue = "h2", matchIfMissing = true)
public class H2UserPointLockRegistrar implements UserPointLockRegistrar {

    private static final String UPSERT = """
            merge into user_point_lock (user_id, created_at) key (user_id) values (?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void registerIfAbsent(Long userId, LocalDateTime now) {
        jdbcTemplate.update(UPSERT, userId, now);
    }
}
