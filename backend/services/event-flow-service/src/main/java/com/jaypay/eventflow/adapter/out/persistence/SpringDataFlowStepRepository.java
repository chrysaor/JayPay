package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface SpringDataFlowStepRepository extends JpaRepository<FlowStepJpaEntity, Long> {

    boolean existsByFlowTypeAndFlowIdAndStep(FlowType flowType, String flowId, FlowStep step);

    List<FlowStepJpaEntity> findByFlowTypeAndFlowId(FlowType flowType, String flowId);

    @Modifying
    @Query("""
            delete from FlowStepJpaEntity s where exists (
                select 1 from FlowJpaEntity f
                where f.flowType = s.flowType and f.flowId = s.flowId and f.updatedAt < :cutoff)""")
    int deleteStepsOfFlowsUpdatedBefore(@Param("cutoff") Instant cutoff);
}
