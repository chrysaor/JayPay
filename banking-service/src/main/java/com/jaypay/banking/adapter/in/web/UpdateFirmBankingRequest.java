package com.jaypay.banking.adapter.in.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateFirmBankingRequest {
    /**
     * UpdateFirmBankingRequest
     * <p>
     * This class is simplified for temporary domain.
     */
    private String firmBankingRequestAggregateIdentifier;
    private int status;

}
