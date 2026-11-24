package com.jaypay.common.membership;

public record MembershipResponse(
        String membershipId,
        String name,
        String email,
        String address,
        boolean valid,
        boolean corp
) {
}
