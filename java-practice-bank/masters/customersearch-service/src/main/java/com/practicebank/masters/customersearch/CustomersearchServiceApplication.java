package com.practicebank.masters.customersearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.customersearch"})
public class CustomersearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomersearchServiceApplication.class, args);
    }
}
