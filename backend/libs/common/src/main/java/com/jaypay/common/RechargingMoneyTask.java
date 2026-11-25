package com.jaypay.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class RechargingMoneyTask {

    private String taskID;
    private String taskName;

    private String membershipID;

    private List<SubTask> subTaskList;

    private String toBankName;

    private String toBankAccountNumber;

    private int moneyAmount;
}
