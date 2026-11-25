package com.jaypay.money.aggregation.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.aggregation.application.port.in.GetMoneySumByAddressCommand;
import com.jaypay.money.aggregation.application.port.in.GetMoneySumByAddressUseCase;
import com.jaypay.money.aggregation.application.port.out.GetMembershipPort;
import com.jaypay.money.aggregation.application.port.out.GetMoneySumPort;
import com.jaypay.money.aggregation.application.port.out.MemberMoney;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UseCase
@RequiredArgsConstructor
@Transactional
public class GetMoneySumByAggregationService implements GetMoneySumByAddressUseCase {

    private final GetMoneySumPort getMoneySumPort;
    private final GetMembershipPort getMembershipPort;

    @Override
    public int getMoneySumByAddress(GetMoneySumByAddressCommand command) {
        String targetAddress = command.address();
        List<String> membershipIds = getMembershipPort.getMembershipByAddress(targetAddress);

        List<List<String>> membershipPartitionList;
        int partitionSize = membershipIds.size();

        if (membershipIds.size() > 100) {
            partitionSize = 100;
        }
        membershipPartitionList = partitionList(membershipIds, partitionSize);

        int sum = 0;
        for (List<String> partitionedList : membershipPartitionList) {
            List<MemberMoney> memberMoneyList = getMoneySumPort.getMoneySumByMembershipIds(partitionedList);

            for (MemberMoney memberMoney : memberMoneyList) {
                sum += memberMoney.balance();
            }
        }

        return sum;
    }

    private static <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        return IntStream.range(0, list.size())
                .boxed()
                .collect(Collectors.groupingBy(index -> index / partitionSize))
                .values()
                .stream()
                .map(indices -> indices.stream().map(list::get).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }
}
