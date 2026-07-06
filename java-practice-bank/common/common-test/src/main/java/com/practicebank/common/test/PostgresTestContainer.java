package com.practicebank.common.test;

import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {

    private static final String IMAGE = "postgres:16-alpine";

    private static PostgresTestContainer instance;

    private PostgresTestContainer() {
        super(IMAGE);
        withDatabaseName("banking_test");
        withUsername("cobol");
        withPassword("cobol");
    }

    public static synchronized PostgresTestContainer getInstance() {
        if (instance == null) {
            instance = new PostgresTestContainer();
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
}
