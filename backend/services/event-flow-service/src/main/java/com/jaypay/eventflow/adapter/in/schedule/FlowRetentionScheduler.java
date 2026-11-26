package com.jaypay.eventflow.adapter.in.schedule;

import com.jaypay.eventflow.application.port.in.ForgetOldFlowsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "flow.retention.schedule.enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
class FlowRetentionScheduler {

    private final ForgetOldFlowsUseCase forgetOldFlowsUseCase;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    void forgetOldFlows() {
        int forgotten = forgetOldFlowsUseCase.forgetOldFlows();
        if (forgotten > 0) {
            log.info("Forgot {} flows whose last step is older than the retention period", forgotten);
        }
    }
}
