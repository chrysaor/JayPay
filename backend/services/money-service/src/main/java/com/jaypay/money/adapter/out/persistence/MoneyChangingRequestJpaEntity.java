package com.jaypay.money.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;


@Entity
@Table(name = "money_changing_request")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoneyChangingRequestJpaEntity {

    @Id
    @GeneratedValue
    private Long moneyChangingRequestId;

    private String targetMembershipId;

    private int moneyChangingType;

    private int moneyAmount;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    private int changingMoneyStatus; // 0: request 1: success, 2: failure

    private String uuid;

    public MoneyChangingRequestJpaEntity(String targetMembershipId, int moneyChangingType, int moneyAmount, Timestamp timestamp, int changingMoneyStatus, UUID uuid) {
        this.targetMembershipId = targetMembershipId;
        this.moneyChangingType = moneyChangingType;
        this.moneyAmount = moneyAmount;
        this.timestamp = timestamp;
        this.changingMoneyStatus = changingMoneyStatus;
        this.uuid = uuid.toString();
    }

}
