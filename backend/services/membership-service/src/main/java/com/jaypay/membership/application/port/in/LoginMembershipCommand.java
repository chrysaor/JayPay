package com.jaypay.membership.application.port.in;

import lombok.Builder;

@Builder
public record LoginMembershipCommand(String membershipId) {
}
