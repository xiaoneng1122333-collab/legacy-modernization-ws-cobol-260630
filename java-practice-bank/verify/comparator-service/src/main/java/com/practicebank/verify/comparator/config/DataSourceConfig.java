package com.practicebank.verify.comparator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 2 つの JDBC データソースを明示設定する.
 *
 * <ul>
 *   <li>{@code dataSource}   : COBOL 側. {@code spring.datasource.*} (primary)</li>
 *   <li>{@code javaDataSource}: Java 側. {@code spring.java.datasource.*}</li>
 * </ul>
 *
 * <p>driver-class-name は URL スキーマ (jdbc:postgresql://) から HikariCP が自動検出する.</p>
 */
@Configuration
public class DataSourceConfig {

    @Primary
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String pwd) {
        return DataSourceBuilder.create()
            .url(url)
            .username(user)
            .password(pwd)
            .driverClassName("org.postgresql.Driver")
            .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public DataSource javaDataSource(
            @Value("${spring.java.datasource.url}") String url,
            @Value("${spring.java.datasource.username}") String user,
            @Value("${spring.java.datasource.password}") String pwd) {
        return DataSourceBuilder.create()
            .url(url)
            .username(user)
            .password(pwd)
            .driverClassName("org.postgresql.Driver")
            .build();
    }

    @Bean
    public JdbcTemplate javaJdbcTemplate(DataSource javaDataSource) {
        return new JdbcTemplate(javaDataSource);
    }
}
