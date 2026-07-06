package com.practicebank.common.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer extends GenericContainer<RedisTestContainer> {

    private static final String IMAGE = "redis:7-alpine";
    private static RedisTestContainer instance;

    private RedisTestContainer() {
        super(DockerImageName.parse(IMAGE));
        withExposedPorts(6379);
    }

    public static synchronized RedisTestContainer getInstance() {
        if (instance == null) {
            instance = new RedisTestContainer();
        }
        return instance;
    }

    @Override
    public void start() {
        super.start();
        System.setProperty("spring.data.redis.url", "redis://" + getHost() + ":" + getMappedPort(6379));
    }
}
