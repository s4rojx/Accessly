package com.assessly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateExpectedSchema() {
        Integer users = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='users'", Integer.class);
        Integer refreshTokens = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='refresh_tokens'", Integer.class);
        Integer questions = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='questions'", Integer.class);
        Integer exams = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='exams'", Integer.class);
        Integer examQuestions = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='exam_questions'", Integer.class);
        Integer examCandidates = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='exam_allowed_candidates'", Integer.class);
        Integer attempts = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='attempts'", Integer.class);
        Integer attemptQuestions = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='attempt_questions'", Integer.class);
        Integer attemptAnswers = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='attempt_answers'", Integer.class);
        Integer results = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name='results'", Integer.class);
        Integer flyway = jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success=true", Integer.class);
        assertThat(users).isEqualTo(1);
        assertThat(refreshTokens).isEqualTo(1);
        assertThat(questions).isEqualTo(1);
        assertThat(exams).isEqualTo(1);
        assertThat(examQuestions).isEqualTo(1);
        assertThat(examCandidates).isEqualTo(1);
        assertThat(attempts).isEqualTo(1);
        assertThat(attemptQuestions).isEqualTo(1);
        assertThat(attemptAnswers).isEqualTo(1);
        assertThat(results).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_schema='public' and table_name='users' and column_name in ('avatar_url', 'avatar_public_id')", Integer.class)).isEqualTo(2);
        assertThat(flyway).isEqualTo(7);
    }
}
