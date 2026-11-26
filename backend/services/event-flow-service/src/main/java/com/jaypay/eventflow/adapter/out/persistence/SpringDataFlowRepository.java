package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.domain.FlowStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface SpringDataFlowRepository extends JpaRepository<FlowJpaEntity, String> {

    @Query("""
            select f from FlowJpaEntity f
            where (:flowType is null or f.flowType = :flowType) and (:status is null or f.status = :status)
            order by f.startedAt desc""")
    List<FlowJpaEntity> findRecent(@Param("flowType") FlowType flowType, @Param("status") FlowStatus status, Limit limit);

    @Modifying
    @Query("delete from FlowJpaEntity f where f.updatedAt < :cutoff")
    int deleteUpdatedBefore(@Param("cutoff") Instant cutoff);
}
