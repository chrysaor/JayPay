package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "flow_step",
        uniqueConstraints = @UniqueConstraint(name = "flow_step_once", columnNames = {"flow_type", "flow_id", "step"}),
        indexes = @Index(name = "flow_step_of_flow", columnList = "flow_type, flow_id"))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowStepJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FlowType flowType;

    @Column(nullable = false)
    private String flowId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FlowStep step;

    @Column(nullable = false)
    private String service;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FlowOutcome outcome;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant occurredAt;

    private String membershipId;

    private Long amount;
}
