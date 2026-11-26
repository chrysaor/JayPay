package com.jaypay.membership.adapter.in.axon;

import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.event.CheckedMembershipEvent;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class CheckMembershipHandler {

    private final FindMembershipUseCase findMembershipUseCase;
    private final EventGateway eventGateway;

    @CommandHandler
    public void handle(CheckMembershipCommand command) {
        boolean valid = findMembershipUseCase.isValidMembership(new FindMembershipCommand(command.membershipId()));
        log.info("Member {} of recharge {} is {}", command.membershipId(), command.rechargingRequestId(), valid ? "valid" : "not a valid member");
        eventGateway.publish(new CheckedMembershipEvent(command.checkMembershipId(), command.rechargingRequestId(), command.membershipId(), valid));
    }
}
