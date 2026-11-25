package com.jaypay.auth.adapter.out.persistence;

import com.jaypay.auth.domain.StaffRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataStaffAccountRepository extends JpaRepository<StaffAccountJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into staff_account (username, password_hash, role, active, mfa_enabled)
            values (:username, :passwordHash, :role, :active, :mfaEnabled)
            on conflict (username) do nothing
            """)
    int insertIfAbsent(@Param("username") String username,
                       @Param("passwordHash") String passwordHash,
                       @Param("role") String role,
                       @Param("active") boolean active,
                       @Param("mfaEnabled") boolean mfaEnabled);

    List<StaffAccountJpaEntity> findAllByOrderByUsernameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StaffAccountJpaEntity> findByRoleAndActiveTrue(StaffRole role);
}
