package com.carbonlens.controller;

import com.carbonlens.dto.*;
import com.carbonlens.model.*;
import com.carbonlens.repository.*;
import com.carbonlens.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final NormalizedRecordRepository normalizedRecordRepository;
    private final ReviewDecisionRepository reviewDecisionRepository;
    private final AuditService auditService;

    @GetMapping("/decisions")
    public ResponseEntity<List<ReviewDecisionDto>> listDecisions(@AuthenticationPrincipal User user) {
        List<ReviewDecisionDto> dtos = reviewDecisionRepository
                .findByRecordTenantOrderByDecidedAtDesc(user.getTenant())
                .stream().map(ReviewDecisionDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/records/{id}/review")
    public ResponseEntity<?> reviewRecord(@AuthenticationPrincipal User user,
                                           @PathVariable UUID id,
                                           @RequestBody ReviewRequest request) {
        Optional<NormalizedRecord> opt = normalizedRecordRepository.findByIdAndTenant(id, user.getTenant());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        NormalizedRecord record = opt.get();
        if (record.getReviewStatus() == NormalizedRecord.ReviewStatus.approved) {
            return ResponseEntity.status(409)
                    .body(Map.of("detail", "This record is already approved and immutable."));
        }

        ReviewDecision.Action action;
        try {
            action = ReviewDecision.Action.valueOf(request.getAction());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Invalid action: '" + request.getAction() + "'."));
        }

        String oldStatus = record.getReviewStatus().name();
        String comment = request.getComment() != null ? request.getComment() : "";

        if (action == ReviewDecision.Action.approve) {
            record.setReviewStatus(NormalizedRecord.ReviewStatus.approved);
            record.setApprovedBy(user);
            record.setApprovedAt(Instant.now());
        } else if (action == ReviewDecision.Action.reject) {
            record.setReviewStatus(NormalizedRecord.ReviewStatus.rejected);
        }
        normalizedRecordRepository.save(record);

        ReviewDecision decision = ReviewDecision.builder()
                .record(record).reviewer(user).action(action).comment(comment).build();
        reviewDecisionRepository.save(decision);

        AuditLog.Action auditAction = action == ReviewDecision.Action.approve
                ? AuditLog.Action.approve : AuditLog.Action.reject;
        auditService.logEvent(user, auditAction, "NormalizedRecord", record.getId().toString(),
                "review_status", oldStatus, record.getReviewStatus().name(), comment);

        return ResponseEntity.ok(Map.of(
                "record_id", record.getId().toString(),
                "action", action.name(),
                "new_status", record.getReviewStatus().name(),
                "decision_id", decision.getId().toString()
        ));
    }

    @PostMapping("/records/bulk-review")
    public ResponseEntity<?> bulkReview(@AuthenticationPrincipal User user,
                                         @RequestBody BulkReviewRequest request) {
        if (request.getRecordIds() == null || request.getRecordIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "No record_ids provided."));
        }

        ReviewDecision.Action action;
        try {
            action = ReviewDecision.Action.valueOf(request.getAction());
            if (action == ReviewDecision.Action.flag) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Invalid action: '" + request.getAction() + "'."));
        }

        String comment = request.getComment() != null ? request.getComment() : "";
        int updated = 0;

        for (UUID recordId : request.getRecordIds()) {
            Optional<NormalizedRecord> opt = normalizedRecordRepository
                    .findByIdAndTenant(recordId, user.getTenant());
            if (opt.isEmpty()) continue;
            NormalizedRecord record = opt.get();
            if (record.getReviewStatus() == NormalizedRecord.ReviewStatus.approved) continue;

            String oldStatus = record.getReviewStatus().name();
            NormalizedRecord.ReviewStatus newStatus = action == ReviewDecision.Action.approve
                    ? NormalizedRecord.ReviewStatus.approved : NormalizedRecord.ReviewStatus.rejected;

            record.setReviewStatus(newStatus);
            if (action == ReviewDecision.Action.approve) {
                record.setApprovedBy(user);
                record.setApprovedAt(Instant.now());
            }
            normalizedRecordRepository.save(record);

            ReviewDecision decision = ReviewDecision.builder()
                    .record(record).reviewer(user).action(action)
                    .comment("[Bulk] " + comment).build();
            reviewDecisionRepository.save(decision);

            AuditLog.Action auditAction = action == ReviewDecision.Action.approve
                    ? AuditLog.Action.approve : AuditLog.Action.reject;
            auditService.logEvent(user, auditAction, "NormalizedRecord", record.getId().toString(),
                    "review_status", oldStatus, newStatus.name(), "[Bulk] " + comment);

            updated++;
        }

        return ResponseEntity.ok(Map.of("updated", updated, "action", action.name()));
    }
}
