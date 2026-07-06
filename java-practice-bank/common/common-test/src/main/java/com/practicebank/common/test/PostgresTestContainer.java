package com.practicebank.common.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers PostgreSQL 16 container for integration tests.
 * System properties (spring.datasource.*) are set after start so Spring Boot
 * auto-configuration picks up the random port.
 */
public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine")
        .asSubstituteFor("postgres");

    private static volatile PostgresTestContainer instance;

    private PostgresTestContainer() {
        super(IMAGE);
        withDatabaseName("banking_test");
        withUsername("cobol");
        withPassword("cobol");
    }

    public static PostgresTestContainer getInstance() {
        if (instance == null) {
            synchronized (PostgresTestContainer.class) {
                if (instance == null) {
                    instance = new PostgresTestContainer();
                }
            }
        }
        return instance;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("spring.datasource.url", getJdbcUrl());
        System.setProperty("spring.datasource.username", getUsername());
        System.setProperty("spring.datasource.password", getPassword());
    }

    @Override
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s",
            getHost(), getMappedPort(POSTGRESQL_PORT), getDatabaseName());
    }
}
