package com.jaypay.common.event;

public record RollbackFirmBankingRefusedEvent(String rollbackFirmBankingId, String membershipId) {
}
