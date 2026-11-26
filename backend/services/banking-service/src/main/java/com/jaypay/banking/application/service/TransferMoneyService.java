package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.TransferMoneyUseCase;
import com.jaypay.banking.application.port.out.BankAnswer;
import com.jaypay.banking.application.port.out.BankTransferPort;
import com.jaypay.banking.application.port.out.ExternalBankPort;
import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.RequestIdReusedException;
import com.jaypay.banking.domain.TransferStatus;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
@Slf4j
public class TransferMoneyService implements TransferMoneyUseCase {

    private final BankTransferPort bankTransferPort;
    private final ExternalBankPort externalBankPort;

    @Override
    @Transactional
    public RecordedTransfer transfer(BankTransfer transfer) {
        bankTransferPort.claim(transfer, TransferStatus.PENDING);
        RecordedTransfer recorded = bankTransferPort.findForUpdate(transfer.requestId());
        if (recorded.status() == TransferStatus.CANCELLED) {
            return new RecordedTransfer(transfer, TransferStatus.CANCELLED);
        }
        if (!recorded.transfer().equals(transfer)) {
            throw new RequestIdReusedException(transfer.requestId());
        }
        return send(recorded);
    }

    @Override
    @Transactional
    public Optional<RecordedTransfer> reverse(String requestId, String reversalRequestId) {
        bankTransferPort.claim(BankTransfer.cancelled(requestId), TransferStatus.CANCELLED);
        RecordedTransfer original = send(bankTransferPort.findForUpdate(requestId));
        if (!original.succeeded()) {
            return Optional.empty();
        }
        BankTransfer reversal = original.transfer().reversed(reversalRequestId);
        bankTransferPort.claim(reversal, TransferStatus.PENDING);
        return Optional.of(send(bankTransferPort.findForUpdate(reversalRequestId)));
    }

    private RecordedTransfer send(RecordedTransfer recorded) {
        if (recorded.isFinal()) {
            return recorded;
        }
        BankAnswer answer = externalBankPort.send(recorded.transfer());
        if (answer == BankAnswer.NO_ANSWER) {
            log.warn("the bank did not answer the transfer {}", recorded.transfer().requestId());
            return recorded;
        }
        TransferStatus status = answer == BankAnswer.ACCEPTED ? TransferStatus.SUCCEEDED : TransferStatus.FAILED;
        bankTransferPort.settle(recorded.transfer().requestId(), status);
        return new RecordedTransfer(recorded.transfer(), status);
    }
}
