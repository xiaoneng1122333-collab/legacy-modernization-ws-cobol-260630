package com.practicebank.masters.calendar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.practicebank.common", "com.practicebank.masters.calendar"})
public class CalendarServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CalendarServiceApplication.class, args);
    }
}
