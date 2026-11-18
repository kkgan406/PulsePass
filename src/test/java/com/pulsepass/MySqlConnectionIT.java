package com.pulsepass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MySqlConnectionIT {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToPulsepassAndExecutesQuery() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result);
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertEquals("pulsepass", database);
    }
}
