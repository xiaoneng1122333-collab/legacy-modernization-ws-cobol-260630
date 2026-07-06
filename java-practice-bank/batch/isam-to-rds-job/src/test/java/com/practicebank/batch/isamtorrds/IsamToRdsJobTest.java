package com.practicebank.batch.isamtorrds;

import com.practicebank.common.test.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class IsamToRdsJobTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.getInstance().start();
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job isamToRdsJob;

    @Test
    void contextLoads_andJobBeanPresent() {
        // Phase 1: verify Spring context loads and the job bean is defined.
        // Full job execution requires ISAM data files (/workspace/subsystems/*/data/*.idx)
        // which are outside this project's scope — covered in Phase 2.
        assertThat(isamToRdsJob).isNotNull();
        assertThat(isamToRdsJob.getName()).isEqualTo("isamToRdsJob");
    }
}
