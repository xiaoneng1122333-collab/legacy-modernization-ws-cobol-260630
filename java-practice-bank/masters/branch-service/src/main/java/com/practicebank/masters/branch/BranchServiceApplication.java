package com.practicebank.masters.branch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.branch"})
public class BranchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BranchServiceApplication.class, args);
    }
}
