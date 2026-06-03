package com.carbonlens.repository;
import com.carbonlens.model.ReviewDecision;
import com.carbonlens.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, UUID> {
    List<ReviewDecision> findByRecordTenantOrderByDecidedAtDesc(Tenant tenant);
}
