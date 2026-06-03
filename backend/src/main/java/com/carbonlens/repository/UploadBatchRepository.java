package com.carbonlens.repository;
import com.carbonlens.model.Tenant;
import com.carbonlens.model.UploadBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadBatchRepository extends JpaRepository<UploadBatch, UUID> {
    Page<UploadBatch> findByTenantOrderByUploadedAtDesc(Tenant tenant, Pageable pageable);
    Optional<UploadBatch> findByIdAndTenant(UUID id, Tenant tenant);
    long countByTenant(Tenant tenant);

    @Query("SELECT b FROM UploadBatch b JOIN FETCH b.dataSource WHERE b.tenant = :tenant ORDER BY b.uploadedAt DESC")
    List<UploadBatch> findRecentByTenant(@Param("tenant") Tenant tenant, Pageable pageable);
}
