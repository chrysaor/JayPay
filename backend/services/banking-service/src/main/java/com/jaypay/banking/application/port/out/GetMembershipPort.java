package com.jaypay.banking.application.port.out;

public interface GetMembershipPort {

    MembershipStatus getMembership(String membershipId);

}
