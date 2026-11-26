package com.jaypay.payment.application.port.out;

public record MembershipStatus(String membershipId, boolean valid, boolean corp) {
}
