package com.carbonlens.controller;

import com.carbonlens.dto.*;
import com.carbonlens.model.*;
import com.carbonlens.repository.*;
import com.carbonlens.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IngestionController {

    private final DataSourceRepository dataSourceRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final IngestionService ingestionService;

    // ── Data Sources ──────────────────────────────────────────────────────────
    @GetMapping("/data-sources")
    public ResponseEntity<List<DataSourceDto>> listDataSources(@AuthenticationPrincipal User user) {
        List<DataSourceDto> results = dataSourceRepository.findByTenant(user.getTenant())
                .stream().map(DataSourceDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/data-sources")
    public ResponseEntity<DataSourceDto> createDataSource(@AuthenticationPrincipal User user,
                                                           @RequestBody DataSourceRequest req) {
        DataSource ds = DataSource.builder()
                .tenant(user.getTenant())
                .name(req.getName())
                .sourceType(DataSource.SourceType.valueOf(req.getSourceType()))
                .description(req.getDescription() != null ? req.getDescription() : "")
                .columnMapping(req.getColumnMapping() != null ? req.getColumnMapping() : Map.of())
                .build();
        return ResponseEntity.status(201).body(DataSourceDto.from(dataSourceRepository.save(ds)));
    }

    // ── Batches ──────────────────────────────────────────────────────────────
    @GetMapping("/batches")
    public ResponseEntity<?> listBatches(@AuthenticationPrincipal User user,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("uploadedAt").descending());
        Page<UploadBatch> batches = uploadBatchRepository.findByTenantOrderByUploadedAtDesc(
                user.getTenant(), pageable);
        List<UploadBatchDto> dtos = batches.getContent().stream()
                .map(UploadBatchDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(PagedResponse.of(dtos, batches.getTotalElements(),
                page, pageSize, batches.hasNext(), batches.hasPrevious()));
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<UploadBatchDto> getBatch(@AuthenticationPrincipal User user,
                                                    @PathVariable UUID id) {
        return uploadBatchRepository.findByIdAndTenant(id, user.getTenant())
                .map(b -> ResponseEntity.ok(UploadBatchDto.from(b)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Uploads ───────────────────────────────────────────────────────────────
    @PostMapping("/upload/sap")
    public ResponseEntity<?> uploadSap(@AuthenticationPrincipal User user,
                                        @RequestParam("data_source_id") UUID dataSourceId,
                                        @RequestParam("file") MultipartFile file) {
        return handleUpload(user, dataSourceId, DataSource.SourceType.sap_export, file, false, null);
    }

    @PostMapping("/upload/utility")
    public ResponseEntity<?> uploadUtility(@AuthenticationPrincipal User user,
                                            @RequestParam("data_source_id") UUID dataSourceId,
                                            @RequestParam("file") MultipartFile file) {
        return handleUpload(user, dataSourceId, DataSource.SourceType.utility_portal, file, false, null);
    }

    @PostMapping("/upload/travel")
    public ResponseEntity<?> uploadTravel(@AuthenticationPrincipal User user,
                                           @RequestParam("data_source_id") UUID dataSourceId,
                                           @RequestParam(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "payload", required = false) String jsonPayload) {
        return handleUpload(user, dataSourceId, DataSource.SourceType.travel_api, file, true, jsonPayload);
    }

    private ResponseEntity<?> handleUpload(User user, UUID dataSourceId,
                                            DataSource.SourceType expectedType,
                                            MultipartFile file, boolean isTravel, String jsonPayload) {
        Optional<DataSource> dsOpt = dataSourceRepository
                .findByIdAndTenantAndSourceType(dataSourceId, user.getTenant(), expectedType);
        if (dsOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("detail", expectedType.getDisplay() + " data source not found."));
        }

        byte[] bytes;
        String filename;
        try {
            if (file != null && !file.isEmpty()) {
                bytes = file.getBytes();
                filename = file.getOriginalFilename();
            } else if (jsonPayload != null) {
                bytes = jsonPayload.getBytes();
                filename = "api_payload.json";
            } else {
                return ResponseEntity.badRequest().body(Map.of("detail", "No file provided."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("detail", "File read failed: " + e.getMessage()));
        }

        UploadBatch batch = UploadBatch.builder()
                .tenant(user.getTenant())
                .dataSource(dsOpt.get())
                .uploadedBy(user)
                .originalFilename(filename)
                .status(UploadBatch.Status.processing)
                .build();
        uploadBatchRepository.save(batch);

        try {
            Map<String, Object> summary;
            if (expectedType == DataSource.SourceType.sap_export) {
                summary = ingestionService.ingestSapCsv(bytes, batch, user);
            } else if (expectedType == DataSource.SourceType.utility_portal) {
                summary = ingestionService.ingestUtilityCsv(bytes, batch, user);
            } else {
                summary = ingestionService.ingestTravelJson(bytes, batch, user);
            }
            return ResponseEntity.status(201).body(Map.of(
                    "batch_id", batch.getId().toString(),
                    "summary", summary
            ));
        } catch (Exception e) {
            batch.setStatus(UploadBatch.Status.failed);
            batch.setErrorLog(e.getMessage());
            uploadBatchRepository.save(batch);
            return ResponseEntity.status(500).body(Map.of("detail", "Ingestion failed: " + e.getMessage()));
        }
    }
}
