package com.jaypay.common.event;

import org.axonframework.commandhandling.RoutingKey;

public record CheckMembershipCommand(String checkMembershipId, String rechargingRequestId, @RoutingKey String membershipId) {
}
