package com.cabzen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end check against the real Neon database configured in .env. */
@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void readsFromNeon() {
        String version = jdbc.queryForObject("select version()", String.class);
        System.out.println("Connected to: " + version);
        assertTrue(version.startsWith("PostgreSQL"));
    }

    @Test
    @Transactional // rolled back after the test; the temp table never outlives it
    void writesAndReadsBack() {
        jdbc.execute("create temp table cabzen_ping (msg text) on commit drop");
        jdbc.update("insert into cabzen_ping values (?)", "hello neon");
        assertEquals("hello neon", jdbc.queryForObject("select msg from cabzen_ping", String.class));
    }
}
