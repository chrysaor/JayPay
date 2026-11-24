package com.jaypay.membership.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.membership.application.port.in.ModifyMembershipCommand;
import com.jaypay.membership.application.port.in.ModifyMembershipUseCase;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class ModifyMembershipController {

    private final ModifyMembershipUseCase modifyMembershipUseCase;

    @PutMapping(path = "/memberships/{membershipId}")
    ResponseEntity<Membership> ModifyMembershipByMemberId(@RequestBody ModifyMembershipRequest request) {

        ModifyMembershipCommand command = ModifyMembershipCommand.builder()
                .membershipId(request.getMembershipId())
                .name(request.getName())
                .email(request.getEmail())
                .address(request.getAddress())
                .isValid(request.isValid())
                .isCorp(request.isCorp())
                .build();

        return ResponseEntity.ok(modifyMembershipUseCase.modifyMembership(command));
    }

}
