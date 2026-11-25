package com.jaypay.money.adapter.axon.event;

public record IncreaseMemberMoneyEvent(String aggregateIdentifier, String targetMembershipId, int amount) {
}
