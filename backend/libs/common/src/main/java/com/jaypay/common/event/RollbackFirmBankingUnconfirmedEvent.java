package com.jaypay.common.event;

public record RollbackFirmBankingUnconfirmedEvent(String rollbackFirmBankingId, String membershipId) {
}
