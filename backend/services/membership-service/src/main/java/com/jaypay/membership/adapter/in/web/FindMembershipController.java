package com.jaypay.membership.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipListByAddressCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
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
    ResponseEntity<MembershipResponse> findMembershipByMemberId(@PathVariable String membershipId) {

        FindMembershipCommand command = FindMembershipCommand.builder()
                .membershipId(membershipId)
                .build();

        return findMembershipUseCase.findMembership(command)
                .map(MembershipResponseMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/memberships/address/{addressName}")
    ResponseEntity<List<MembershipResponse>> findMembershipListByAddress(@PathVariable String addressName) {

        FindMembershipListByAddressCommand command = FindMembershipListByAddressCommand.builder()
                .addressName(addressName)
                .build();

        return ResponseEntity.ok(findMembershipUseCase.findMembershipListByAddress(command).stream()
                .map(MembershipResponseMapper::toResponse)
                .toList());
    }

}
