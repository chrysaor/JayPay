package com.jaypay.banking.adapter.axon.command;

import lombok.*;


@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateRegisteredBankAccountCommand {

    private String membershipId;
    private String bankName;
    private String bankAccountNumber;

}
