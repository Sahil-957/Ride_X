package com.ridex.config;

import com.ridex.config.DatabaseConfig.NeonUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NeonUrlTest {

    @Test
    void convertsNeonUrlToJdbc() {
        // Fake values on an example.com host, so secret scanners don't mistake it for a real Neon URL.
        NeonUrl url = NeonUrl.parse(
                "postgresql://test_user:p%40ss:w0rd@db.example.com/testdb?sslmode=require&channel_binding=require");

        assertEquals("jdbc:postgresql://db.example.com/testdb?sslmode=require&channel_binding=require",
                url.jdbcUrl());
        assertEquals("test_user", url.username());
        assertEquals("p@ss:w0rd", url.password());
    }

    @Test
    void keepsPortAndStripsDotenvQuotes() {
        NeonUrl url = NeonUrl.parse("\"postgres://u:p@localhost:5433/db\"");

        assertEquals("jdbc:postgresql://localhost:5433/db", url.jdbcUrl());
        assertEquals("u", url.username());
        assertEquals("p", url.password());
    }

    @Test
    void rejectsUrlWithoutCredentials() {
        assertThrows(IllegalStateException.class, () -> NeonUrl.parse("postgresql://host/db"));
    }
}
