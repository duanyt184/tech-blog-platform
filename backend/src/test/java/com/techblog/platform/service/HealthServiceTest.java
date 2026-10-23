package com.techblog.platform.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthServiceTest {

    @Test
    void shouldAcceptSuccessfulDatabaseQuery() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(1);
        HealthService healthService = new HealthService(jdbcTemplate);

        healthService.verifyDatabaseConnection();

        assertThat(jdbcTemplate.lastSql).isEqualTo("SELECT 1");
    }

    @Test
    void shouldRejectUnexpectedDatabaseResult() {
        HealthService healthService = new HealthService(new StubJdbcTemplate(0));

        assertThatThrownBy(healthService::verifyDatabaseConnection)
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {

        private final Integer result;
        private String lastSql;

        private StubJdbcTemplate(Integer result) {
            this.result = result;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            lastSql = sql;
            return requiredType.cast(result);
        }
    }
}

