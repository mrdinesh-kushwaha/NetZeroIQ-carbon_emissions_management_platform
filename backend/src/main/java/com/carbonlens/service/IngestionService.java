package com.carbonlens.service;

import com.carbonlens.model.*;
import com.carbonlens.repository.*;
import com.carbonlens.service.NormalizationService.NormalizationException;
import com.carbonlens.service.NormalizationService.NormalizationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final RawRecordRepository rawRecordRepository;
    private final NormalizedRecordRepository normalizedRecordRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final NormalizationService normalizationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // Default SAP column mappings
    private static final Map<String, String> DEFAULT_SAP_COLUMN_MAP = new LinkedHashMap<>();
    static {
        DEFAULT_SAP_COLUMN_MAP.put("Menge", "quantity");
        DEFAULT_SAP_COLUMN_MAP.put("Menge (ME)", "quantity");
        DEFAULT_SAP_COLUMN_MAP.put("Quantity", "quantity");
        DEFAULT_SAP_COLUMN_MAP.put("quantity", "quantity");
        DEFAULT_SAP_COLUMN_MAP.put("ME", "unit");
        DEFAULT_SAP_COLUMN_MAP.put("Einheit", "unit");
        DEFAULT_SAP_COLUMN_MAP.put("UoM", "unit");
        DEFAULT_SAP_COLUMN_MAP.put("Unit", "unit");
        DEFAULT_SAP_COLUMN_MAP.put("unit", "unit");
        DEFAULT_SAP_COLUMN_MAP.put("Materialbeschreibung", "material_description");
        DEFAULT_SAP_COLUMN_MAP.put("Material Description", "material_description");
        DEFAULT_SAP_COLUMN_MAP.put("Bezeichnung", "material_description");
        DEFAULT_SAP_COLUMN_MAP.put("material_description", "material_description");
        DEFAULT_SAP_COLUMN_MAP.put("Werk", "plant_code");
        DEFAULT_SAP_COLUMN_MAP.put("Plant", "plant_code");
        DEFAULT_SAP_COLUMN_MAP.put("plant_code", "plant_code");
        DEFAULT_SAP_COLUMN_MAP.put("Buchungsdatum", "posting_date");
        DEFAULT_SAP_COLUMN_MAP.put("Posting Date", "posting_date");
        DEFAULT_SAP_COLUMN_MAP.put("Belegdatum", "posting_date");
        DEFAULT_SAP_COLUMN_MAP.put("posting_date", "posting_date");
        DEFAULT_SAP_COLUMN_MAP.put("Date", "posting_date");
        DEFAULT_SAP_COLUMN_MAP.put("Belegnummer", "document_number");
        DEFAULT_SAP_COLUMN_MAP.put("Document Number", "document_number");
        DEFAULT_SAP_COLUMN_MAP.put("Beleg", "document_number");
        DEFAULT_SAP_COLUMN_MAP.put("document_number", "document_number");
        DEFAULT_SAP_COLUMN_MAP.put("Doc No", "document_number");
    }

    private static final Map<String, String> UTILITY_COLUMN_MAP = Map.ofEntries(
        Map.entry("Meter ID", "meter_id"), Map.entry("meter_id", "meter_id"),
        Map.entry("MeterID", "meter_id"), Map.entry("Meter", "meter_id"),
        Map.entry("Period Start", "billing_start"), Map.entry("billing_start", "billing_start"),
        Map.entry("From", "billing_start"),
        Map.entry("Period End", "billing_end"), Map.entry("billing_end", "billing_end"),
        Map.entry("To", "billing_end"),
        Map.entry("Consumption (kWh)", "consumption_kwh"), Map.entry("Consumption", "consumption_kwh"),
        Map.entry("consumption_kwh", "consumption_kwh"), Map.entry("kWh", "consumption_kwh"),
        Map.entry("Units", "consumption_kwh"), Map.entry("Unit", "unit"),
        Map.entry("Tariff Type", "tariff_type"), Map.entry("tariff_type", "tariff_type"),
        Map.entry("Tariff", "tariff_type"), Map.entry("Site", "site_name"),
        Map.entry("Location", "site_name")
    );

    // ── SAP ingestion ────────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> ingestSapCsv(byte[] fileBytes, UploadBatch batch, User actor) {
        String text = decodeBytes(fileBytes);
        Map<String, String> customMap = batch.getDataSource().getColumnMapping() != null
                ? batch.getDataSource().getColumnMapping() : Map.of();

        Map<String, String> headerMapping = new LinkedHashMap<>(DEFAULT_SAP_COLUMN_MAP);
        headerMapping.putAll(customMap);

        List<RawRecord> rawRecords = new ArrayList<>();
        List<NormalizedRecord> normalizedRecords = new ArrayList<>();
        int flagged = 0;
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new StringReader(text))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("CSV file appears empty or has no headers");

            String[] row;
            int idx = 1;
            while ((row = reader.readNext()) != null) {
                Map<String, String> rawRow = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < row.length; i++) {
                    rawRow.put(headers[i], row[i]);
                }

                // Map to canonical field names
                Map<String, String> canonicalRow = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : rawRow.entrySet()) {
                    String canonical = headerMapping.getOrDefault(e.getKey(), e.getKey());
                    canonicalRow.put(canonical, e.getValue());
                }

                RawRecord rr = RawRecord.builder()
                        .batch(batch).rowIndex(idx)
                        .rawData(new LinkedHashMap<>(rawRow))
                        .parseError("").build();

                try {
                    NormalizationResult result = normalizationService.normalizeSapRecord(canonicalRow);
                    rawRecords.add(rr);

                    NormalizedRecord nr = buildNormalizedRecord(batch, rr, "sap_export", result);
                    if (result.suspiciousFlag()) flagged++;
                    normalizedRecords.add(nr);
                } catch (NormalizationException e) {
                    rr.setParseError(e.getMessage());
                    rawRecords.add(rr);
                    errors.add("Row " + idx + ": " + e.getMessage());
                }
                idx++;
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV parsing failed: " + e.getMessage(), e);
        }

        return saveBatch(batch, rawRecords, normalizedRecords, flagged, errors, actor,
                "SAP CSV ingestion complete");
    }

    // ── Utility ingestion ────────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> ingestUtilityCsv(byte[] fileBytes, UploadBatch batch, User actor) {
        String text = decodeBytes(fileBytes);
        List<RawRecord> rawRecords = new ArrayList<>();
        List<NormalizedRecord> normalizedRecords = new ArrayList<>();
        int flagged = 0;
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new StringReader(text))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("CSV file appears empty or has no headers");

            String[] row;
            int idx = 1;
            while ((row = reader.readNext()) != null) {
                Map<String, String> rawRow = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < row.length; i++) {
                    rawRow.put(headers[i], row[i]);
                }
                Map<String, String> canonicalRow = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : rawRow.entrySet()) {
                    canonicalRow.put(UTILITY_COLUMN_MAP.getOrDefault(e.getKey(), e.getKey()), e.getValue());
                }

                RawRecord rr = RawRecord.builder()
                        .batch(batch).rowIndex(idx)
                        .rawData(new LinkedHashMap<>(rawRow))
                        .parseError("").build();

                try {
                    NormalizationResult result = normalizationService.normalizeUtilityRecord(canonicalRow);
                    rawRecords.add(rr);
                    NormalizedRecord nr = buildNormalizedRecord(batch, rr, "utility_portal", result);
                    if (result.suspiciousFlag()) flagged++;
                    normalizedRecords.add(nr);
                } catch (NormalizationException e) {
                    rr.setParseError(e.getMessage());
                    rawRecords.add(rr);
                    errors.add("Row " + idx + ": " + e.getMessage());
                }
                idx++;
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV parsing failed: " + e.getMessage(), e);
        }

        return saveBatch(batch, rawRecords, normalizedRecords, flagged, errors, actor,
                "Utility CSV ingestion complete");
    }

    // ── Travel JSON ingestion ────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> ingestTravelJson(byte[] jsonBytes, UploadBatch batch, User actor) {
        List<Map<String, Object>> bookings;
        try {
            String jsonText = new String(jsonBytes, StandardCharsets.UTF_8);
            Object parsed = objectMapper.readValue(jsonText, Object.class);
            if (parsed instanceof List<?> list) {
                bookings = (List<Map<String, Object>>) list;
            } else if (parsed instanceof Map<?, ?> map) {
                Object inner = map.get("bookings");
                if (inner == null) inner = map.get("trips");
                if (inner == null) inner = map.get("records");
                if (inner instanceof List<?>) bookings = (List<Map<String, Object>>) inner;
                else throw new RuntimeException("Travel JSON must be a list or object with a 'bookings' key");
            } else {
                throw new RuntimeException("Invalid JSON structure");
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON payload: " + e.getMessage(), e);
        }

        List<RawRecord> rawRecords = new ArrayList<>();
        List<NormalizedRecord> normalizedRecords = new ArrayList<>();
        int flagged = 0;
        List<String> errors = new ArrayList<>();

        int idx = 1;
        for (Map<String, Object> booking : bookings) {
            RawRecord rr = RawRecord.builder()
                    .batch(batch).rowIndex(idx)
                    .rawData(booking)
                    .parseError("").build();

            try {
                NormalizationResult result = normalizationService.normalizeTravelRecord(booking);
                rawRecords.add(rr);
                NormalizedRecord nr = buildNormalizedRecord(batch, rr, "travel_api", result);
                if (result.suspiciousFlag()) flagged++;
                normalizedRecords.add(nr);
            } catch (NormalizationException e) {
                rr.setParseError(e.getMessage());
                rawRecords.add(rr);
                errors.add("Record " + idx + ": " + e.getMessage());
            }
            idx++;
        }

        return saveBatch(batch, rawRecords, normalizedRecords, flagged, errors, actor,
                "Travel JSON ingestion complete");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private NormalizedRecord buildNormalizedRecord(UploadBatch batch, RawRecord rr,
                                                    String sourceType, NormalizationResult r) {
        NormalizedRecord.Scope scope = NormalizedRecord.Scope.valueOf(r.scopeCategory());
        return NormalizedRecord.builder()
                .tenant(batch.getTenant()).batch(batch).rawRecord(rr)
                .sourceType(sourceType).sourceRowId(r.sourceRowId())
                .activityType(r.activityType()).scopeCategory(scope)
                .originalUnit(r.originalUnit()).originalValue(r.originalValue())
                .normalizedUnit(r.normalizedUnit()).normalizedValue(r.normalizedValue())
                .emissionFactor(r.emissionFactor()).estimatedEmissions(r.estimatedEmissions())
                .suspiciousFlag(r.suspiciousFlag()).suspiciousReason(r.suspiciousReason())
                .reviewStatus(NormalizedRecord.ReviewStatus.pending)
                .periodStart(r.periodStart()).periodEnd(r.periodEnd())
                .build();
    }

    private Map<String, Object> saveBatch(UploadBatch batch, List<RawRecord> rawRecords,
                                           List<NormalizedRecord> normalizedRecords,
                                           int flagged, List<String> errors,
                                           User actor, String logNote) {
        rawRecordRepository.saveAll(rawRecords);

        // Assign raw record FK after save (IDs now assigned)
        for (NormalizedRecord nr : normalizedRecords) {
            // rawRecord already set during build
        }
        normalizedRecordRepository.saveAll(normalizedRecords);

        batch.setTotalRows(rawRecords.size());
        batch.setProcessedRows(normalizedRecords.size());
        batch.setFlaggedRows(flagged);
        batch.setStatus(UploadBatch.Status.complete);
        batch.setCompletedAt(Instant.now());
        if (!errors.isEmpty()) {
            batch.setErrorLog(batch.getErrorLog() + String.join("\n", errors));
        }
        uploadBatchRepository.save(batch);

        auditService.logEvent(actor, AuditLog.Action.ingest, "UploadBatch", batch.getId().toString(),
                logNote + ". " + normalizedRecords.size() + "/" + rawRecords.size()
                        + " rows normalised, " + flagged + " flagged.");

        List<String> displayErrors = errors.size() > 10 ? errors.subList(0, 10) : errors;
        return Map.of(
                "total_rows", rawRecords.size(),
                "processed_rows", normalizedRecords.size(),
                "flagged_rows", flagged,
                "error_count", errors.size(),
                "errors", displayErrors
        );
    }

    private String decodeBytes(byte[] bytes) {
        try { return new String(bytes, "UTF-8"); }
        catch (Exception e) { return new String(bytes, Charset.forName("ISO-8859-1")); }
    }
}
