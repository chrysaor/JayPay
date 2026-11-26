package com.jaypay.common.event;

public record CheckedMembershipEvent(String checkMembershipId, String rechargingRequestId, String membershipId, boolean valid) {
}
