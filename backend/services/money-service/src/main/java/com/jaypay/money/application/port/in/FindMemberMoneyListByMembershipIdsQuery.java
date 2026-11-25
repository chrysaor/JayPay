package com.jaypay.money.application.port.in;

import java.util.List;
import java.util.Objects;

public record FindMemberMoneyListByMembershipIdsQuery(List<String> membershipIds) {

    public FindMemberMoneyListByMembershipIdsQuery {
        Objects.requireNonNull(membershipIds, "membershipIds");
    }
}
