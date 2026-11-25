package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;

import java.util.List;

public interface GetMemberMoneyListPort {

    List<MemberMoney> getMemberMoneys(List<String> membershipIds);

}
