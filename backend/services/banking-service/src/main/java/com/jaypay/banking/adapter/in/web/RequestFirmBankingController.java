package com.jaypay.banking.adapter.in.web;

import com.jaypay.banking.application.port.in.TransferMoneyUseCase;
import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.TransferStatus;
import com.jaypay.common.WebAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@WebAdapter
@RestController
public class RequestFirmBankingController {

    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final TransferMoneyUseCase transferMoneyUseCase;
    private final BigDecimal maxAmount;

    public RequestFirmBankingController(TransferMoneyUseCase transferMoneyUseCase, @Value("${banking.amount.max}") long maxAmount) {
        this.transferMoneyUseCase = transferMoneyUseCase;
        this.maxAmount = BigDecimal.valueOf(maxAmount);
    }

    @PostMapping(path = "/banking/firmbanking/request")
    TransferDetail requestFirmBanking(@RequestBody TransferRequest request) {
        RecordedTransfer recorded = transferMoneyUseCase.transfer(new BankTransfer(
                requestId(request.requestId()),
                required(request.fromBankName()),
                required(request.fromBankAccountNumber()),
                required(request.toBankName()),
                required(request.toBankAccountNumber()),
                amount(request.moneyAmount())));
        if (!recorded.isFinal()) {
            throw new TransferNotConfirmedException();
        }
        return TransferDetail.of(recorded);
    }

    private static String requestId(String value) {
        if (value == null || !REQUEST_ID.matcher(value).matches()) {
            throw new TransferRequestRejected("INVALID_REQUEST_ID", "Send a requestId of 1 to 64 letters, digits, '-' or '_'.");
        }
        return value;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new TransferRequestRejected("INVALID_BANK_ACCOUNT", "Send the bank name and account number of both sides.");
        }
        return value;
    }

    private long amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(maxAmount) > 0) {
            throw new TransferRequestRejected("INVALID_AMOUNT", "Send a moneyAmount that is a whole number from 1 to " + maxAmount.toPlainString() + ".");
        }
        return value.longValueExact();
    }

    record TransferRequest(
            String requestId,
            String fromBankName,
            String fromBankAccountNumber,
            String toBankName,
            String toBankAccountNumber,
            BigDecimal moneyAmount) {
    }

    record TransferDetail(
            String requestId,
            String fromBankName,
            String fromBankAccountNumber,
            String toBankName,
            String toBankAccountNumber,
            long moneyAmount,
            TransferStatus transferStatus) {

        static TransferDetail of(RecordedTransfer recorded) {
            BankTransfer transfer = recorded.transfer();
            TransferStatus status = recorded.status() == TransferStatus.CANCELLED ? TransferStatus.FAILED : recorded.status();
            return new TransferDetail(transfer.requestId(), transfer.fromBankName(), transfer.fromBankAccountNumber(),
                    transfer.toBankName(), transfer.toBankAccountNumber(), transfer.amount(), status);
        }
    }
}
