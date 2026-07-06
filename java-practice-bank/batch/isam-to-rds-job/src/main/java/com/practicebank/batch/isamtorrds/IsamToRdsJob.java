package com.practicebank.batch.isamtorrds;

import com.practicebank.common.batch.BatchJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(BatchJobConfig.class)
public class IsamToRdsJob {
    public static void main(String[] args) {
        SpringApplication.run(IsamToRdsJob.class, args);
    }
    // Steps added in Task 9
}
