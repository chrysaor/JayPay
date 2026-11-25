package com.jaypay.auth.adapter.out.persistence;

import com.jaypay.auth.domain.StaffAccount;
import com.jaypay.auth.domain.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "staff_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class StaffAccountJpaEntity {

    @Id
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    static StaffAccountJpaEntity of(StaffAccount account) {
        return new StaffAccountJpaEntity(account.username(), account.passwordHash(), account.role(), account.active(), account.mfaEnabled());
    }

    StaffAccount toDomain() {
        return new StaffAccount(username, passwordHash, role, active, mfaEnabled);
    }
}
