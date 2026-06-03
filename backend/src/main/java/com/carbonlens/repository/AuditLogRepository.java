package com.carbonlens.repository;
import com.carbonlens.model.AuditLog;
import com.carbonlens.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop200ByActorTenantOrderByTimestampDesc(Tenant tenant);
    List<AuditLog> findByObjectTypeAndObjectIdOrderByTimestampDesc(String objectType, String objectId);
}
