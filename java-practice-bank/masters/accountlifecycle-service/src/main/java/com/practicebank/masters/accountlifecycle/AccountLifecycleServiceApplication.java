package com.practicebank.masters.accountlifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.accountlifecycle"})
public class AccountLifecycleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountLifecycleServiceApplication.class, args);
    }
}
