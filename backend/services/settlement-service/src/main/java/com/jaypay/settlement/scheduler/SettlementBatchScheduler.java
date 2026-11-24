package com.jaypay.settlement.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementBatchScheduler {

    private final JobOperator jobOperator;
    private final Job settlementJob;

    @Scheduled(fixedRate = 30000)
    public void runSettlementJob() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobOperator.start(settlementJob, jobParameters);
    }

}
