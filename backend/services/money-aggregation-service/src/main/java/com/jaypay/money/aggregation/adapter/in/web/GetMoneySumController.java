package com.jaypay.money.aggregation.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.money.aggregation.application.port.in.GetMoneySumByAddressCommand;
import com.jaypay.money.aggregation.application.port.in.GetMoneySumByAddressUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class GetMoneySumController {
    private final GetMoneySumByAddressUseCase getMoneySumByAddressUseCase;
    @PostMapping(path = "/money/aggregation/get-money-sum-by-address")
    int getMoneySumByAddress(@RequestBody GetMoneySumByAddressRequest request) {

        return getMoneySumByAddressUseCase.getMoneySumByAddress(
                GetMoneySumByAddressCommand.builder()
                        .address(request.getAddress()).build()
        );
    }
}
