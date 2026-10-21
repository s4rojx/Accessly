package com.assessly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void authenticationMigrationsCreateOnlyExpectedTables() {
        Integer users = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='users'", Integer.class);
        Integer refreshTokens = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='refresh_tokens'", Integer.class);
        Integer flyway = jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success=true", Integer.class);
        assertThat(users).isEqualTo(1);
        assertThat(refreshTokens).isEqualTo(1);
        assertThat(flyway).isEqualTo(2);
    }
}
