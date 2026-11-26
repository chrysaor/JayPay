package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataFlowStepRepository extends JpaRepository<FlowStepJpaEntity, Long> {

    boolean existsByFlowTypeAndFlowIdAndStep(FlowType flowType, String flowId, FlowStep step);

    List<FlowStepJpaEntity> findByFlowTypeAndFlowId(FlowType flowType, String flowId);
}
