package com.jaypay.money.query.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.query.application.port.in.QueryMoneySumByRegionQuery;
import com.jaypay.money.query.application.port.in.QueryMoneySumByRegionUseCase;
import com.jaypay.money.query.application.port.out.GetMoneySumByAddressPort;
import com.jaypay.money.query.domain.MoneySumByRegion;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@UseCase
@RequiredArgsConstructor
public class QueryMoneySumByRegionService implements QueryMoneySumByRegionUseCase {

    private final GetMoneySumByAddressPort getMoneySumByAddressPort;

    @Override
    public MoneySumByRegion queryMoneySumByRegion(QueryMoneySumByRegionQuery query) {
        return MoneySumByRegion.generateMoneySumByRegion(
                new MoneySumByRegion.MoneySumByRegionId(UUID.randomUUID().toString()),
                new MoneySumByRegion.RegionName(query.address()),
                new MoneySumByRegion.MoneySum(getMoneySumByAddressPort.getMoneySumByAddressPort(query.address()))
        );
    }

}
