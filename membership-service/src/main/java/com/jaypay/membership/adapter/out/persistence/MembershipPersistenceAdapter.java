package com.jaypay.membership.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.membership.adapter.out.vault.VaultAdapter;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.application.port.out.ModifyMembershipPort;
import com.jaypay.membership.application.port.out.RegisterMembershipPort;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@PersistenceAdapter
@RequiredArgsConstructor
public class MembershipPersistenceAdapter implements RegisterMembershipPort, FindMembershipPort, ModifyMembershipPort {

    private final SpringDataMembershipRepository membershipRepository;
    private final VaultAdapter vaultAdapter;

    @Override
    public MembershipJpaEntity createMembership(Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp) {
        MembershipJpaEntity membershipJpaEntity = new MembershipJpaEntity(
                membershipName.getMembershipName(),
                vaultAdapter.encryptData(membershipEmail.getMembershipEmail()),
                membershipAddress.getMembershipAddress(),
                membershipIsValid.isMembershipIsValid(),
                membershipIsCorp.isMembershipIsCorp(),
                ""
        );
        membershipRepository.save(membershipJpaEntity);

        // Make clone
        MembershipJpaEntity clone = membershipJpaEntity.clone();
        clone.setEmail(membershipEmail.getMembershipEmail());

        return clone;
    }

    @Override
    public MembershipJpaEntity findMembership(Membership.MembershipId membershipId) {
        MembershipJpaEntity membershipJpaEntity =  membershipRepository.getById(Long.parseLong(membershipId.getMembershipId()));
        String encryptedEmailString = membershipJpaEntity.getEmail();
        String decryptedEmailString = vaultAdapter.decryptData(encryptedEmailString);

        MembershipJpaEntity clone = membershipJpaEntity.clone();
        clone.setEmail(decryptedEmailString);

        return clone;
    }

    @Override
    public List<MembershipJpaEntity> findMembershipListByAddress(Membership.MembershipAddress membershipAddress) {
        String address = membershipAddress.getMembershipAddress();
        List<MembershipJpaEntity> membershipJpaEntityList = membershipRepository.findByAddress(address);
        List<MembershipJpaEntity> cloneList = new ArrayList<>();

        for (MembershipJpaEntity membershipJpaEntity : membershipJpaEntityList) {
            String encryptedEmailString = membershipJpaEntity.getEmail();
            String decryptedEmailString = vaultAdapter.decryptData(encryptedEmailString);

            MembershipJpaEntity clone = membershipJpaEntity.clone();
            clone.setEmail(decryptedEmailString);
            cloneList.add(clone);
        }

        return cloneList;
    }

    @Override
    public MembershipJpaEntity modifyMembership(Membership.MembershipId membershipId, Membership.MembershipName membershipName, Membership.MembershipEmail membershipEmail, Membership.MembershipAddress membershipAddress, Membership.MembershipIsValid membershipIsValid, Membership.MembershipIsCorp membershipIsCorp, Membership.MembershipRefreshToken membershipRefreshToken) {
        MembershipJpaEntity entity = membershipRepository.getById(Long.parseLong(membershipId.getMembershipId()));
        String originalEmail = membershipEmail.getMembershipEmail();

        entity.setName(membershipName.getMembershipName());
        entity.setEmail(vaultAdapter.encryptData(membershipEmail.getMembershipEmail()));
        entity.setAddress(membershipAddress.getMembershipAddress());
        entity.setCorp(membershipIsCorp.isMembershipIsCorp());
        entity.setValid(membershipIsValid.isMembershipIsValid());
        entity.setRefreshToken(membershipRefreshToken.getRefreshToken());

        membershipRepository.save(entity);

        MembershipJpaEntity clone = entity.clone();
        clone.setEmail(membershipEmail.getMembershipEmail());

        return clone;
    }

}
