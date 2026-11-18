package com.factori.platform.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantDatabaseContext {
    private final JdbcTemplate jdbcTemplate;

    public TenantDatabaseContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void activate(UUID tenantId) {
        jdbcTemplate.queryForObject(
                "select set_config('app.tenant_id', ?, true)",
                String.class,
                tenantId.toString());
    }
}
