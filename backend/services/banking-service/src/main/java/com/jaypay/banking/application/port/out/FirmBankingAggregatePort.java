package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.FirmBankingTransfer;

import java.util.concurrent.CompletableFuture;

public interface FirmBankingAggregatePort {

    CompletableFuture<String> createFirmBankingAggregate(FirmBankingTransfer transfer);

    CompletableFuture<String> updateFirmBankingAggregate(String aggregateIdentifier, int firmBankingStatus);

}
