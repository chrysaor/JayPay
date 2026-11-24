package com.jaypay.banking.adapter.out.service;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class Membership {
    /*
     * For banking service. It is same domain but different aggregate.
     */
    private String membershipId;
    private String name;
    private String email;
    private String address;
    private boolean isValid;
    private boolean isCorp;
}
