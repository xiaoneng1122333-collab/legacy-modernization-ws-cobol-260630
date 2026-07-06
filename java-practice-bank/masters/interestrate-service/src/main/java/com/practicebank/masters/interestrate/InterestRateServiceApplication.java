package com.practicebank.masters.interestrate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.interestrate"})
public class InterestRateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterestRateServiceApplication.class, args);
    }
}
