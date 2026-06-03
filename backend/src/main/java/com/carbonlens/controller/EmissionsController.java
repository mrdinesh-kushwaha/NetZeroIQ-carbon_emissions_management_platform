package com.carbonlens.controller;

import com.carbonlens.dto.*;
import com.carbonlens.model.*;
import com.carbonlens.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmissionsController {

    private final NormalizedRecordRepository normalizedRecordRepository;
    private final UploadBatchRepository uploadBatchRepository;

    @GetMapping("/records")
    public ResponseEntity<?> listRecords(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String scopeCategory,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean suspiciousFlag,
            @RequestParam(required = false) UUID batch,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "-created_at") String ordering) {

        Specification<NormalizedRecord> spec = buildSpec(user.getTenant(), reviewStatus,
                scopeCategory, sourceType, suspiciousFlag, batch, search);

        Sort sort = parseOrdering(ordering);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        Page<NormalizedRecord> records = normalizedRecordRepository.findAll(spec, pageable);

        List<NormalizedRecordDto> dtos = records.getContent().stream()
                .map(NormalizedRecordDto::from).collect(Collectors.toList());

        return ResponseEntity.ok(PagedResponse.of(dtos, records.getTotalElements(),
                page, pageSize, records.hasNext(), records.hasPrevious()));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<NormalizedRecordDto> getRecord(@AuthenticationPrincipal User user,
                                                          @PathVariable UUID id) {
        return normalizedRecordRepository.findByIdAndTenant(id, user.getTenant())
                .map(r -> ResponseEntity.ok(NormalizedRecordDto.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDto> dashboardStats(@AuthenticationPrincipal User user) {
        Tenant tenant = user.getTenant();

        long total = normalizedRecordRepository.countByTenant(tenant);
        long pending = normalizedRecordRepository.countByTenantAndReviewStatus(tenant, NormalizedRecord.ReviewStatus.pending);
        long approved = normalizedRecordRepository.countByTenantAndReviewStatus(tenant, NormalizedRecord.ReviewStatus.approved);
        long rejected = normalizedRecordRepository.countByTenantAndReviewStatus(tenant, NormalizedRecord.ReviewStatus.rejected);
        long suspicious = normalizedRecordRepository.countByTenantAndSuspiciousFlagTrue(tenant);

        List<Object[]> scopeData = normalizedRecordRepository.sumEmissionsByScope(tenant, NormalizedRecord.ReviewStatus.approved);
        List<DashboardStatsDto.ScopeEmissions> byScope = scopeData.stream()
                .map(row -> DashboardStatsDto.ScopeEmissions.builder()
                        .scopeCategory(row[0].toString())
                        .totalEmissions(((Number) row[1]).doubleValue())
                        .count(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());

        long totalBatches = uploadBatchRepository.countByTenant(tenant);
        List<UploadBatch> recentBatches = uploadBatchRepository.findRecentByTenant(
                tenant, PageRequest.of(0, 5));
        List<UploadBatchDto> recentDtos = recentBatches.stream()
                .map(UploadBatchDto::from).collect(Collectors.toList());

        return ResponseEntity.ok(DashboardStatsDto.builder()
                .records(DashboardStatsDto.RecordStats.builder()
                        .total(total).pending(pending).approved(approved)
                        .rejected(rejected).suspicious(suspicious).build())
                .batches(DashboardStatsDto.BatchStats.builder()
                        .total(totalBatches).recent(recentDtos).build())
                .emissionsByScope(byScope)
                .build());
    }

    private Specification<NormalizedRecord> buildSpec(Tenant tenant, String reviewStatus,
            String scopeCategory, String sourceType, Boolean suspiciousFlag,
            UUID batchId, String search) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenant"), tenant));
            if (reviewStatus != null && !reviewStatus.isBlank())
                predicates.add(cb.equal(root.get("reviewStatus"),
                        NormalizedRecord.ReviewStatus.valueOf(reviewStatus)));
            if (scopeCategory != null && !scopeCategory.isBlank())
                predicates.add(cb.equal(root.get("scopeCategory"),
                        NormalizedRecord.Scope.valueOf(scopeCategory)));
            if (sourceType != null && !sourceType.isBlank())
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            if (suspiciousFlag != null)
                predicates.add(cb.equal(root.get("suspiciousFlag"), suspiciousFlag));
            if (batchId != null)
                predicates.add(cb.equal(root.get("batch").get("id"), batchId));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("activityType")), pattern),
                        cb.like(cb.lower(root.get("sourceRowId")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Sort parseOrdering(String ordering) {
        if (ordering.startsWith("-")) {
            return Sort.by(Sort.Direction.DESC, toCamel(ordering.substring(1)));
        }
        return Sort.by(Sort.Direction.ASC, toCamel(ordering));
    }

    private String toCamel(String snakeCase) {
        return switch (snakeCase) {
            case "created_at" -> "createdAt";
            case "estimated_emissions" -> "estimatedEmissions";
            case "original_value" -> "originalValue";
            default -> snakeCase;
        };
    }
}
