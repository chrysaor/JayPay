package com.jaypay.money.domain;

public record MoneyChangingRequest(
        RequestId requestId,
        String targetMembershipId,
        MoneyChangingType type,
        MoneyAmount amount,
        MoneyChangingStatus status
) {

    public static MoneyChangingRequest pending(RequestId requestId, String targetMembershipId, MoneyChangingType type, MoneyAmount amount) {
        return new MoneyChangingRequest(requestId, targetMembershipId, type, amount, MoneyChangingStatus.PENDING);
    }

    public MoneyChangingRequest settled(boolean applied) {
        return new MoneyChangingRequest(requestId, targetMembershipId, type, amount,
                applied ? MoneyChangingStatus.SUCCEEDED : MoneyChangingStatus.FAILED);
    }

    public boolean sameChangeAs(MoneyChangingRequest other) {
        return targetMembershipId.equals(other.targetMembershipId) && type == other.type && amount.equals(other.amount);
    }

    public enum MoneyChangingType {
        INCREASE(1),
        DECREASE(2);

        private final int code;

        MoneyChangingType(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public enum MoneyChangingStatus {
        PENDING(0),
        SUCCEEDED(1),
        FAILED(2),
        ROLLBACK_UNCONFIRMED(3);

        private final int code;

        MoneyChangingStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
