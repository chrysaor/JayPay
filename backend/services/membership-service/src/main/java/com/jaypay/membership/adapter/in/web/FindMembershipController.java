package com.jaypay.membership.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipListByAddressCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class FindMembershipController {

    private final FindMembershipUseCase findMembershipUseCase;

    @GetMapping(path = "/memberships/{membershipId}")
    ResponseEntity<Membership> findMembershipByMemberId(@PathVariable String membershipId) {

        FindMembershipCommand command = FindMembershipCommand.builder()
                .membershipId(membershipId)
                .build();

        return ResponseEntity.ok(findMembershipUseCase.findMembership(command));
    }

    @GetMapping(path = "/memberships/address/{addressName}")
    ResponseEntity<List<Membership>> findMembershipListByAddress(@PathVariable String addressName) {

        FindMembershipListByAddressCommand command = FindMembershipListByAddressCommand.builder()
                .addressName(addressName)
                .build();

        return ResponseEntity.ok(findMembershipUseCase.findMembershipListByAddress(command));
    }

}
