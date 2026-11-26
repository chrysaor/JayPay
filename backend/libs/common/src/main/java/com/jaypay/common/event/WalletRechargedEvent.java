package com.jaypay.common.event;

public record WalletRechargedEvent(String rechargingRequestId, String membershipId, long amount) {
}
