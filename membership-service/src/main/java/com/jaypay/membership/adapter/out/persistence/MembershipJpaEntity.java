package com.jaypay.membership.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "membership")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MembershipJpaEntity {

    @Id
    @GeneratedValue
    private Long membershipId;
    private String name;
    private String email;
    private String address;
    private boolean isValid;
    private boolean isCorp;
    private String refreshToken;

    public MembershipJpaEntity(String name, String email, String address, boolean isValid, boolean isCorp, String refreshToken) {
        this.name = name;
        this.address = address;
        this.email = email;
        this.isValid = isValid;
        this.isCorp = isCorp;
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        return "MembershipJpaEntity{" +
                "membershipId=" + membershipId +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", address='" + address + '\'' +
                ", isValid=" + isValid +
                ", isCorp=" + isCorp +
                ", refreshToken='" + refreshToken + '\'' +
                '}';
    }

    public MembershipJpaEntity clone() {
        try {
            super.clone();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new MembershipJpaEntity(this.membershipId, this.name, this.email, this.address, this.isValid, this.isCorp, this.refreshToken);
    }
}
