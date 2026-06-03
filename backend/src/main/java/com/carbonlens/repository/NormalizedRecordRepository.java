package com.carbonlens.repository;
import com.carbonlens.model.NormalizedRecord;
import com.carbonlens.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NormalizedRecordRepository extends JpaRepository<NormalizedRecord, UUID>,
        JpaSpecificationExecutor<NormalizedRecord> {
    Optional<NormalizedRecord> findByIdAndTenant(UUID id, Tenant tenant);
    long countByTenant(Tenant tenant);
    long countByTenantAndReviewStatus(Tenant tenant, NormalizedRecord.ReviewStatus status);
    long countByTenantAndSuspiciousFlagTrue(Tenant tenant);

    @Query("SELECT n.scopeCategory, SUM(n.estimatedEmissions), COUNT(n) FROM NormalizedRecord n " +
           "WHERE n.tenant = :tenant AND n.reviewStatus = :status GROUP BY n.scopeCategory")
    List<Object[]> sumEmissionsByScope(@Param("tenant") Tenant tenant,
                                       @Param("status") NormalizedRecord.ReviewStatus status);
}
