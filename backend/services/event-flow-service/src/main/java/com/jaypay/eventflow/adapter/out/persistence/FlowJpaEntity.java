package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.eventflow.domain.FlowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "flow", indexes = {
        @Index(name = "flow_by_start", columnList = "started_at"),
        @Index(name = "flow_by_update", columnList = "updated_at")})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowJpaEntity {

    @Id
    private String flowKey;

    @Column(nullable = false)
    private String flowType;

    @Column(nullable = false)
    private String flowId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FlowStatus status;

    private String membershipId;

    private Long amount;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private String lastStep;

    private String failedStep;

    private int stepCount;
}
