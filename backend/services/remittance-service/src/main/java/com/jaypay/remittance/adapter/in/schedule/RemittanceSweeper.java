package com.jaypay.remittance.adapter.in.schedule;

import com.jaypay.remittance.application.port.in.ResumeUnfinishedRemittancesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RemittanceSweeper {

    private final ResumeUnfinishedRemittancesUseCase resumeUnfinishedRemittancesUseCase;

    @Scheduled(fixedDelayString = "${remittance.sweep.every}")
    void sweep() {
        resumeUnfinishedRemittancesUseCase.resumeUnfinishedRemittances();
    }
}
