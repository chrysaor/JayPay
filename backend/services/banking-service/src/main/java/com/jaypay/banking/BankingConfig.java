package com.jaypay.banking;


import com.jaypay.banking.domain.CompanyAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.jaypay.common")
public class BankingConfig {

    @Bean
    CompanyAccount companyAccount(@Value("${banking.company-account.bank-name}") String bankName,
                                  @Value("${banking.company-account.bank-account-number}") String bankAccountNumber) {
        return new CompanyAccount(bankName, bankAccountNumber);
    }
}
