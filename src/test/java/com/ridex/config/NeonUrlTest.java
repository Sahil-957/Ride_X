package com.ridex.config;

import com.ridex.config.DatabaseConfig.NeonUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NeonUrlTest {

    @Test
    void convertsNeonUrlToJdbc() {
        NeonUrl url = NeonUrl.parse(
                "postgresql://neondb_owner:p%40ss:w0rd@ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

        assertEquals("jdbc:postgresql://ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require",
                url.jdbcUrl());
        assertEquals("neondb_owner", url.username());
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
