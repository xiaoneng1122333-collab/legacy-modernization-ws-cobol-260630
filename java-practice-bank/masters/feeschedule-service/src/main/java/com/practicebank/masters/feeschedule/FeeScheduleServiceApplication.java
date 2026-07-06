package com.practicebank.masters.feeschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.feeschedule"})
public class FeeScheduleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeeScheduleServiceApplication.class, args);
    }
}
