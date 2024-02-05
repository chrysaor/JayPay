package com.jaypay.money.aggregation.application.port.out;

import java.util.List;

public interface GetMoneySumPort {
    List<MemberMoney> getMoneySumByMembershipIds(List<String> membershipIds);
}
