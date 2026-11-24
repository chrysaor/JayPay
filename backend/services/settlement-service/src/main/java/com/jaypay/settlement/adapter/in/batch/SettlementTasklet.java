package com.jaypay.settlement.adapter.in.batch;

import com.jaypay.settlement.application.port.in.SettlementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementTasklet implements Tasklet {

    private final SettlementUseCase settlementUseCase;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        settlementUseCase.settleNormalStatusPayments();
        return RepeatStatus.FINISHED;
    }
}
