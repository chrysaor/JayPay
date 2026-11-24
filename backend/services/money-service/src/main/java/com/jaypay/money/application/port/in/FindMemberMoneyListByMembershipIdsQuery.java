package com.jaypay.money.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class FindMemberMoneyListByMembershipIdsQuery extends SelfValidating<FindMemberMoneyListByMembershipIdsQuery> {

    @NotNull
    private final List<String> membershipIds;

    public FindMemberMoneyListByMembershipIdsQuery(@NotNull List<String> membershipIds) {
        this.membershipIds = membershipIds;
        this.validateSelf();
    }

}