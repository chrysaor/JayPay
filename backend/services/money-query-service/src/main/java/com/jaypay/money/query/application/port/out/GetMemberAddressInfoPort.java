package com.jaypay.money.query.application.port.out;

import java.util.Optional;

public interface GetMemberAddressInfoPort {
    Optional<MemberAddressInfo> getMemberAddressInfo(String membershipId);
}
