package com.jaypay.settlement.tasklet;

import com.jaypay.settlement.adapter.out.service.Payment;
import com.jaypay.settlement.port.out.GetRegisteredBankAccountPort;
import com.jaypay.settlement.port.out.PaymentPort;
import com.jaypay.settlement.port.out.RegisteredBankAccountAggregateIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SettlementTasklet implements Tasklet {

    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final PaymentPort paymentPort;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext){
        List<Payment> normalStatusPaymentList = paymentPort.getNormalStatusPayments();

        Map<String, FirmBankingRequestInfo> franchiseIdToBankAccountMap = new HashMap<>();
        for (Payment payment : normalStatusPaymentList) {
            RegisteredBankAccountAggregateIdentifier entity = getRegisteredBankAccountPort.getRegisteredBankAccount(payment.getFranchiseId());
            franchiseIdToBankAccountMap.put(
                    payment.getFranchiseId(),
                    new FirmBankingRequestInfo(entity.getBankName(), entity.getBankAccountNumber())
            );
        }

        for (Payment payment : normalStatusPaymentList) {
            FirmBankingRequestInfo firmbankingRequestInfo = franchiseIdToBankAccountMap.get(payment.getFranchiseId());
            double fee = Double.parseDouble(payment.getFranchiseFeeRate());
            int calculatedPrice = (int) ((100 - fee) * payment.getRequestPrice() * 100);
            firmbankingRequestInfo.setMoneyAmount(firmbankingRequestInfo.getMoneyAmount() + calculatedPrice);
        }

        for (FirmBankingRequestInfo firmbankingRequestInfo : franchiseIdToBankAccountMap.values()) {
            getRegisteredBankAccountPort.requestFirmbanking(
                    firmbankingRequestInfo.getBankName(),
                    firmbankingRequestInfo.getBankAccountNumber(),
                    firmbankingRequestInfo.getMoneyAmount()
            );
        }

        for (Payment payment : normalStatusPaymentList) {
            paymentPort.finishSettlement(payment.getPaymentId());
        }

        return RepeatStatus.FINISHED;
    }
}
