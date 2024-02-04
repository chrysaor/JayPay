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
    public MembershipJpaEntity createMembership(Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp) {
        return membershipRepository.save(
                new MembershipJpaEntity(
                        membershipName.getMembershipName(),
                        membershipEmail.getMembershipEmail(),
                        membershipAddress.getMembershipAddress(),
                        membershipIsValid.isMembershipIsValid(),
                        membershipIsCorp.isMembershipIsCorp()
                )
        );
    }

    @Override
    public MembershipJpaEntity findMembership(Membership.MembershipId membershipId) {
        return membershipRepository.getById(Long.parseLong(membershipId.getMembershipId()));
    }

    @Override
    public List<MembershipJpaEntity> findMembershipListByAddress(Membership.MembershipAddress membershipAddress) {
        String address = membershipAddress.getMembershipAddress();
        return membershipRepository.findByAddress(address);
    }

    @Override
    public MembershipJpaEntity modifyMembership(Membership.MembershipId membershipId, Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp) {
        MembershipJpaEntity entity = membershipRepository.getById(Long.parseLong(membershipId.getMembershipId()));
        entity.setName(membershipName.getMembershipName());
        entity.setEmail(membershipEmail.getMembershipEmail());
        entity.setAddress(membershipAddress.getMembershipAddress());
        entity.setCorp(membershipIsCorp.isMembershipIsCorp());
        entity.setValid(membershipIsValid.isMembershipIsValid());

        return membershipRepository.save(entity);
    }
}
