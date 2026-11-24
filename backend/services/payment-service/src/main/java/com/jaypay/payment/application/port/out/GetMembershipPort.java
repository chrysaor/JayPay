package com.jaypay.payment.application.port.out;

public interface GetMembershipPort {
    MembershipStatus getMembership(String membershipId);
}
