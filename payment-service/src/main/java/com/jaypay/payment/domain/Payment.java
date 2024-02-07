package com.jaypay.payment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import java.util.Date;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    private Long paymentId;
    private String requestMembershipId;
    private int requestPrice;
    private String franchiseId;
    private String franchiseFeeRate;
    private int paymentStatus; // 0: approved, 1: failed, 2: settlement finished
    private Date approvedAt;

    public static Payment generatePayment (
            PaymentId paymentId,
            RequestMembershipId requestMembershipId,
            RequestPrice requestPrice,
            FranchiseId franchiseId,
            FranchiseFeeRate franchiseFeeRate,
            PaymentStatus paymentStatus,
            ApprovedAt approvedAt
    ){
        return new Payment(
                paymentId.getPaymentId(),
                requestMembershipId.getRequestMembershipId(),
                requestPrice.getRequestPrice(),
                franchiseId.getFranchiseId(),
                franchiseFeeRate.getFranchiseFeeRate(),
                paymentStatus.getPaymentStatus(),
                approvedAt.getApprovedAt()
        );
    }

    @Value
    public static class PaymentId {
        public PaymentId(long value) {
            this.paymentId = value;
        }
        long paymentId ;
    }

    @Value
    public static class RequestMembershipId {
        public RequestMembershipId(String value) {
            this.requestMembershipId = value;
        }
        String requestMembershipId ;
    }

    @Value
    public static class RequestPrice {
        public RequestPrice(int value) {
            this.requestPrice = value;
        }
        int requestPrice ;
    }

    @Value
    public static class FranchiseId {
        public FranchiseId(String value) {
            this.franchiseId = value;
        }
        String franchiseId ;
    }

    @Value
    public static class FranchiseFeeRate {
        public FranchiseFeeRate(String value) {
            this.franchiseFeeRate = value;
        }
        String franchiseFeeRate ;
    }

    @Value
    public static class PaymentStatus {
        public PaymentStatus(int value) {
            this.paymentStatus = value;
        }
        int paymentStatus ;
    }

    @Value
    public static class ApprovedAt {
        public ApprovedAt(Date value) {
            this.approvedAt = value;
        }
        Date approvedAt ;
    }

}
