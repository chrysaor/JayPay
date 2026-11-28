package com.jaypay.membership.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.membership.application.port.in.AuthMembershipUseCase;
import com.jaypay.membership.application.port.in.LoginMembershipCommand;
import com.jaypay.membership.application.port.in.MembershipNotFoundException;
import com.jaypay.membership.application.port.in.MembershipNotValidException;
import com.jaypay.membership.application.port.in.TokenRefusedException;
import com.jaypay.membership.application.port.in.RefreshTokenCommand;
import com.jaypay.membership.application.port.in.ValidateTokenCommand;
import com.jaypay.membership.application.port.out.AuthMembershipPort;
import com.jaypay.membership.application.port.out.EncryptionPort;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.application.port.out.ModifyMembershipPort;
import com.jaypay.membership.domain.JwtToken;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;

@RequiredArgsConstructor
@UseCase
@Transactional
public class AuthMembershipService implements AuthMembershipUseCase {
    private final AuthMembershipPort authMembershipPort;
    private final FindMembershipPort findMembershipPort;
    private final ModifyMembershipPort modifyMembershipPort;
    private final EncryptionPort encryptionPort;

    @Override
    public JwtToken loginMembership(LoginMembershipCommand command) {
        String membershipId = command.membershipId();
        Membership.MembershipId id = new Membership.MembershipId(membershipId);
        Membership membership = findMembershipPort.findMembership(id)
                .orElseThrow(() -> new MembershipNotFoundException(membershipId));
        if (!membership.isValid()) {
            throw new MembershipNotValidException(membershipId);
        }
        String jwtToken = authMembershipPort.generateJwtToken(id);
        String refreshToken = authMembershipPort.generateRefreshToken(id);
        modifyMembershipPort.modifyMembership(
                id,
                new Membership.MembershipName(membership.getName()),
                new Membership.MembershipEmail(membership.getEmail()),
                new Membership.MembershipAddress(membership.getAddress()),
                new Membership.MembershipIsValid(membership.isValid()),
                new Membership.MembershipIsCorp(membership.isCorp()),
                new Membership.MembershipRefreshToken(refreshToken)
        );
        return JwtToken.generateJwtToken(
                new JwtToken.MembershipId(membershipId),
                new JwtToken.MembershipJwtToken(jwtToken),
                new JwtToken.MembershipRefreshToken(refreshToken)
        );
    }

    @Override
    public JwtToken refreshJwtTokenByRefreshToken(RefreshTokenCommand command) {
        String refreshToken = command.refreshToken();
        Membership membership = memberOf(refreshToken);
        if (!refreshToken.equals(membership.getRefreshToken())) {
            throw new TokenRefusedException("This is not the refresh token of the last sign-in.");
        }
        if (!membership.isValid()) {
            throw new MembershipNotValidException(membership.getMembershipId());
        }
        Membership.MembershipId id = new Membership.MembershipId(membership.getMembershipId());
        return JwtToken.generateJwtToken(
                new JwtToken.MembershipId(membership.getMembershipId()),
                new JwtToken.MembershipJwtToken(authMembershipPort.generateJwtToken(id)),
                new JwtToken.MembershipRefreshToken(refreshToken)
        );
    }

    @Override
    public boolean validateJwtToken(ValidateTokenCommand command) {
        return authMembershipPort.validateJwtToken(command.jwtToken());
    }

    @Override
    public Membership getMembershipByJwtToken(ValidateTokenCommand command) {
        Membership membership = memberOf(command.jwtToken());
        return membership.withEmail(encryptionPort.decrypt(membership.getEmail()));
    }

    private Membership memberOf(String token) {
        if (!authMembershipPort.validateJwtToken(token)) {
            throw new TokenRefusedException("The token is not one this service signed, or it has expired.");
        }
        return findMembershipPort.findMembership(authMembershipPort.parseMembershipIdFromToken(token))
                .orElseThrow(() -> new TokenRefusedException("The member of this token does not exist."));
    }
}
