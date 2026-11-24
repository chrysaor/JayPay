package com.jaypay.membership.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.application.port.out.ModifyMembershipPort;
import com.jaypay.membership.application.port.out.RegisterMembershipPort;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import java.util.List;

@PersistenceAdapter
@RequiredArgsConstructor
public class MembershipPersistenceAdapter implements RegisterMembershipPort, FindMembershipPort, ModifyMembershipPort {

    private final SpringDataMembershipRepository membershipRepository;

    @Override
    public Membership.MembershipId createMembership(Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp) {
        MembershipJpaEntity saved = membershipRepository.save(new MembershipJpaEntity(
                membershipName.getMembershipName(),
                membershipEmail.getMembershipEmail(),
                membershipAddress.getMembershipAddress(),
                membershipIsValid.isMembershipIsValid(),
                membershipIsCorp.isMembershipIsCorp(),
                ""
        ));
        return new Membership.MembershipId(saved.getMembershipId().toString());
    }

    @Override
    public Membership findMembership(Membership.MembershipId membershipId) {
        return toDomain(membershipRepository.getReferenceById(Long.parseLong(membershipId.getMembershipId())));
    }

    @Override
    public List<Membership> findMembershipListByAddress(Membership.MembershipAddress membershipAddress) {
        return membershipRepository.findByAddress(membershipAddress.getMembershipAddress()).stream()
                .map(MembershipPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void modifyMembership(Membership.MembershipId membershipId, Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp, Membership.MembershipRefreshToken membershipRefreshToken) {
        MembershipJpaEntity entity = membershipRepository.getReferenceById(Long.parseLong(membershipId.getMembershipId()));

        entity.setName(membershipName.getMembershipName());
        entity.setEmail(membershipEmail.getMembershipEmail());
        entity.setAddress(membershipAddress.getMembershipAddress());
        entity.setCorp(membershipIsCorp.isMembershipIsCorp());
        entity.setValid(membershipIsValid.isMembershipIsValid());
        entity.setRefreshToken(membershipRefreshToken.getRefreshToken());

        membershipRepository.save(entity);
    }

    private static Membership toDomain(MembershipJpaEntity entity) {
        return Membership.generateMember(
                new Membership.MembershipId(entity.getMembershipId().toString()),
                new Membership.MembershipName(entity.getName()),
                new Membership.MembershipEmail(entity.getEmail()),
                new Membership.MembershipAddress(entity.getAddress()),
                new Membership.MembershipIsValid(entity.isValid()),
                new Membership.MembershipIsCorp(entity.isCorp()),
                new Membership.MembershipRefreshToken(entity.getRefreshToken())
        );
    }
}
