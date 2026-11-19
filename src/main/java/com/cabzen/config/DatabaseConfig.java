package com.cabzen.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Builds the DataSource from the URL that `neon env pull` / `neon deploy` writes into .env.
 * Neon uses libpq style (postgresql://user:password@host/db?sslmode=require), while JDBC needs
 * jdbc:postgresql://host/db with the credentials passed separately.
 * Pool settings still come from spring.datasource.hikari.* in application.yml.
 */
@Configuration
public class DatabaseConfig {

    // ponytail: direct (unpooled) URL for app + Flyway. Flyway needs session locks and Hikari already pools;
    // switch the app to the pooled DATABASE_URL if CabZen ever runs many instances.
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(@Value("${DATABASE_URL_UNPOOLED}") String neonUrl) {
        NeonUrl url = NeonUrl.parse(neonUrl);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url.jdbcUrl());
        dataSource.setUsername(url.username());
        dataSource.setPassword(url.password());
        return dataSource;
    }

    record NeonUrl(String jdbcUrl, String username, String password) {

        static NeonUrl parse(String raw) {
            String value = raw.strip();
            if (value.length() > 1 && (value.startsWith("\"") || value.startsWith("'"))) {
                value = value.substring(1, value.length() - 1); // dotenv files may quote values
            }
            URI uri = URI.create(value);
            String userInfo = uri.getUserInfo(); // percent-decoded
            if (uri.getHost() == null || userInfo == null || !userInfo.contains(":")) {
                throw new IllegalStateException(
                        "Database URL must look like postgresql://user:password@host/db?sslmode=require");
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            String[] credentials = userInfo.split(":", 2);
            return new NeonUrl("jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query,
                    credentials[0], credentials[1]);
        }
    }
}
