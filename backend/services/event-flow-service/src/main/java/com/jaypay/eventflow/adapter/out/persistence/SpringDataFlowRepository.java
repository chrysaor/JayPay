package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.eventflow.domain.FlowStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataFlowRepository extends JpaRepository<FlowJpaEntity, String> {

    List<FlowJpaEntity> findByOrderByStartedAtDesc(Limit limit);

    List<FlowJpaEntity> findByStatusOrderByStartedAtDesc(FlowStatus status, Limit limit);
}
