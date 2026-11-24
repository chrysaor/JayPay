package com.jaypay.money.query.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.query.adapter.axon.QueryMoneySumByAddress;
import com.jaypay.money.query.application.port.in.QueryMoneySumByRegionQuery;
import com.jaypay.money.query.application.port.in.QueryMoneySumByRegionUseCase;
import com.jaypay.money.query.domain.MoneySumByRegion;
import lombok.RequiredArgsConstructor;
import org.axonframework.queryhandling.QueryGateway;

import javax.transaction.Transactional;
import java.util.concurrent.ExecutionException;


@UseCase
@RequiredArgsConstructor
@Transactional
public class QueryMoneySumByRegionService implements QueryMoneySumByRegionUseCase {

    private final QueryGateway queryGateway;

    @Override
    public MoneySumByRegion queryMoneySumByRegion(QueryMoneySumByRegionQuery query) {
        try {
            return queryGateway.query(new QueryMoneySumByAddress(query.getAddress())
                    , MoneySumByRegion.class).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
