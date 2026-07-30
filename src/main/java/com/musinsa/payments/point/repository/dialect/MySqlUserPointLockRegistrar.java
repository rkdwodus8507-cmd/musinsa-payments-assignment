package com.musinsa.payments.point.repository.dialect;

import com.musinsa.payments.point.service.UserPointLockRegistrar;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "point.database", name = "dialect", havingValue = "mysql")
public class MySqlUserPointLockRegistrar implements UserPointLockRegistrar {

    private static final String UPSERT = """
            insert into user_point_lock (user_id, created_at) values (?, ?)
            on duplicate key update user_id = user_id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void registerIfAbsent(Long userId, LocalDateTime now) {
        jdbcTemplate.update(UPSERT, userId, now);
    }
}
