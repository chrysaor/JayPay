package com.jaypay.money.query.application.port.in;

import com.jaypay.money.query.domain.MoneySumByRegion;

public interface QueryMoneySumByRegionUseCase {
    MoneySumByRegion queryMoneySumByRegion(QueryMoneySumByRegionQuery query);
}
