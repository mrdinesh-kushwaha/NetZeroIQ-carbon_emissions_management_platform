package com.carbonlens.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Normalization service - Java port of the Python normalization module.
 *
 * Responsibilities:
 *   1. Convert source units to canonical unit (kg, kWh, km)
 *   2. Map activity descriptions to standard activity_type codes
 *   3. Assign Scope 1/2/3 classification
 *   4. Apply emission factors (DEFRA 2023 approximations)
 *   5. Flag suspicious rows with specific reasons
 *
 * All emission factors are kg CO2e per unit.
 */
@Service
public class NormalizationService {

    // ── Emission factors (kg CO2e per unit) ─────────────────────────────────
    public static final Map<String, Double> EMISSION_FACTORS = Map.ofEntries(
        // Scope 1 — fuel combustion
        Map.entry("diesel_combustion", 2.6391),
        Map.entry("petrol_combustion", 2.3122),
        Map.entry("natural_gas_combustion", 2.0431),
        Map.entry("lpg_combustion", 1.5551),
        Map.entry("heating_oil_combustion", 2.5196),
        // Scope 2 — electricity
        Map.entry("electricity_consumption", 0.20707),
        // Scope 3 — travel
        Map.entry("flight_economy_short", 0.15530),
        Map.entry("flight_economy_long", 0.19085),
        Map.entry("flight_business_short", 0.42867),
        Map.entry("flight_business_long", 0.53382),
        Map.entry("hotel_stay", 31.0),
        Map.entry("ground_taxi", 0.14869),
        Map.entry("ground_rail", 0.03549),
        Map.entry("ground_rental_car", 0.16844)
    );

    private static final Map<String, Double> UNIT_CONVERSIONS = Map.ofEntries(
        Map.entry("l", 1.0), Map.entry("liter", 1.0), Map.entry("litre", 1.0),
        Map.entry("liters", 1.0), Map.entry("litres", 1.0), Map.entry("ml", 0.001),
        Map.entry("gal", 3.78541), Map.entry("gallon", 3.78541), Map.entry("gallons", 3.78541),
        Map.entry("m3", 1000.0), Map.entry("cbm", 1000.0),
        Map.entry("kwh", 1.0), Map.entry("mwh", 1000.0), Map.entry("gwh", 1_000_000.0),
        Map.entry("mj", 0.000277778), Map.entry("gj", 0.277778),
        Map.entry("kg", 1.0), Map.entry("kilogram", 1.0), Map.entry("kilograms", 1.0),
        Map.entry("g", 0.001), Map.entry("gram", 0.001),
        Map.entry("t", 1000.0), Map.entry("tonne", 1000.0), Map.entry("tonnes", 1000.0),
        Map.entry("ton", 907.185),
        Map.entry("km", 1.0), Map.entry("kilometer", 1.0), Map.entry("kilometres", 1.0),
        Map.entry("mi", 1.60934), Map.entry("mile", 1.60934), Map.entry("miles", 1.60934),
        Map.entry("nm", 1.852)
    );

    private static final Map<String, String> GERMAN_UNIT_MAP = Map.of(
        "L", "l", "Liter", "l", "Ltr", "l",
        "KG", "kg", "T", "t",
        "KWH", "kwh", "M3", "m3", "CBM", "m3", "KM", "km", "ST", "unit"
    );

    private static final Map<String, String> SAP_MATERIAL_PATTERNS = new LinkedHashMap<>();
    static {
        SAP_MATERIAL_PATTERNS.put("diesel|dies|go|gasoil", "diesel_combustion");
        SAP_MATERIAL_PATTERNS.put("petrol|benzin|gasolin", "petrol_combustion");
        SAP_MATERIAL_PATTERNS.put("erdgas|nat.*gas|natural.?gas", "natural_gas_combustion");
        SAP_MATERIAL_PATTERNS.put("lpg|fluessiggas|liquid.?propan", "lpg_combustion");
        SAP_MATERIAL_PATTERNS.put("heizöl|heizoel|heating.?oil|fuel.?oil", "heating_oil_combustion");
    }

    private static final Map<String, Long> AIRPORT_DISTANCES = new HashMap<>();
    static {
        AIRPORT_DISTANCES.put("LHR-JFK", 5541L); AIRPORT_DISTANCES.put("JFK-LHR", 5541L);
        AIRPORT_DISTANCES.put("LHR-CDG", 344L);  AIRPORT_DISTANCES.put("CDG-LHR", 344L);
        AIRPORT_DISTANCES.put("LHR-FRA", 636L);  AIRPORT_DISTANCES.put("FRA-LHR", 636L);
        AIRPORT_DISTANCES.put("LHR-DXB", 5503L); AIRPORT_DISTANCES.put("DXB-LHR", 5503L);
        AIRPORT_DISTANCES.put("JFK-LAX", 3983L);  AIRPORT_DISTANCES.put("LAX-JFK", 3983L);
        AIRPORT_DISTANCES.put("JFK-ORD", 1190L);  AIRPORT_DISTANCES.put("ORD-JFK", 1190L);
        AIRPORT_DISTANCES.put("FRA-SIN", 10369L); AIRPORT_DISTANCES.put("SIN-FRA", 10369L);
        AIRPORT_DISTANCES.put("LHR-SIN", 10841L); AIRPORT_DISTANCES.put("SIN-LHR", 10841L);
        AIRPORT_DISTANCES.put("CDG-BOM", 7019L);  AIRPORT_DISTANCES.put("BOM-CDG", 7019L);
        AIRPORT_DISTANCES.put("LHR-BOM", 7194L);  AIRPORT_DISTANCES.put("BOM-LHR", 7194L);
        AIRPORT_DISTANCES.put("ORD-LHR", 6347L);  AIRPORT_DISTANCES.put("LHR-ORD", 6347L);
        AIRPORT_DISTANCES.put("LAX-NRT", 8759L);  AIRPORT_DISTANCES.put("NRT-LAX", 8759L);
    }

    private static final Set<String> VALID_IATA = Set.of(
        "LHR","LGW","MAN","EDI","BHX","JFK","LAX","ORD","SFO","BOS","SEA","MIA","DFW","ATL",
        "CDG","AMS","FRA","MUC","MAD","FCO","ZRH","VIE","CPH","DXB","SIN","HKG","NRT","ICN",
        "BOM","DEL","PEK","SYD","MEL","GRU","EZE","YYZ","YVR"
    );

    private static final Map<String, String> TRAVEL_MODE_MAP = new java.util.HashMap<>();
    static {
        TRAVEL_MODE_MAP.put("air", "flight");
        TRAVEL_MODE_MAP.put("flight", "flight");
        TRAVEL_MODE_MAP.put("plane", "flight");
        TRAVEL_MODE_MAP.put("hotel", "hotel_stay");
        TRAVEL_MODE_MAP.put("accommodation", "hotel_stay");
        TRAVEL_MODE_MAP.put("taxi", "ground_taxi");
        TRAVEL_MODE_MAP.put("cab", "ground_taxi");
        TRAVEL_MODE_MAP.put("uber", "ground_taxi");
        TRAVEL_MODE_MAP.put("rail", "ground_rail");
        TRAVEL_MODE_MAP.put("train", "ground_rail");
        TRAVEL_MODE_MAP.put("rental", "ground_rental_car");
        TRAVEL_MODE_MAP.put("car rental", "ground_rental_car");
        TRAVEL_MODE_MAP.put("rental car", "ground_rental_car");
    }

    // ── Result record ────────────────────────────────────────────────────────
    public record NormalizationResult(
        String activityType, String scopeCategory,
        String originalUnit, double originalValue,
        String normalizedUnit, double normalizedValue,
        double emissionFactor, double estimatedEmissions,
        boolean suspiciousFlag, String suspiciousReason,
        LocalDate periodStart, LocalDate periodEnd,
        String sourceRowId, List<String> parseWarnings
    ) {}

    public static class NormalizationException extends RuntimeException {
        public NormalizationException(String msg) { super(msg); }
    }

    // ── Unit helpers ─────────────────────────────────────────────────────────
    private String normalizeUnitLabel(String rawUnit) {
        String cleaned = rawUnit.strip();
        return GERMAN_UNIT_MAP.getOrDefault(cleaned, cleaned.toLowerCase());
    }

    private double[] convertToCanonical(double value, String rawUnit) {
        String unit = normalizeUnitLabel(rawUnit);
        Double factor = UNIT_CONVERSIONS.get(unit);
        if (factor == null) throw new NormalizationException("Unknown unit: '" + rawUnit + "'");
        return new double[]{value * factor};
    }

    private String getCanonicalUnit(String rawUnit) {
        return normalizeUnitLabel(rawUnit);
    }

    // ── SAP normalization ────────────────────────────────────────────────────
    public NormalizationResult normalizeSapRecord(Map<String, String> row) {
        String material = row.getOrDefault("material_description", "").toLowerCase().strip();
        String quantityRaw = row.getOrDefault("quantity", "");
        String unitRaw = row.getOrDefault("unit", "").strip();
        String docNumber = row.getOrDefault("document_number", "");
        String postingDate = row.getOrDefault("posting_date", "");

        String activityType = null;
        for (Map.Entry<String, String> entry : SAP_MATERIAL_PATTERNS.entrySet()) {
            if (Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE).matcher(material).find()) {
                activityType = entry.getValue();
                break;
            }
        }

        if (activityType == null) {
            return new NormalizationResult(
                "unknown_fuel", "scope_1", unitRaw, 0.0, unitRaw, 0.0, 0.0, 0.0,
                true, "Unrecognized material description: '" + material + "'",
                null, null, docNumber, List.of()
            );
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityRaw.replace(",", ".").strip());
        } catch (NumberFormatException e) {
            throw new NormalizationException("Cannot parse quantity: '" + quantityRaw + "'");
        }

        double normalizedValue;
        String canonicalUnit;
        List<String> warnings = new ArrayList<>();
        try {
            normalizedValue = convertToCanonical(quantity, unitRaw)[0];
            canonicalUnit = getCanonicalUnit(unitRaw);
        } catch (NormalizationException e) {
            warnings.add(e.getMessage());
            normalizedValue = quantity;
            canonicalUnit = unitRaw;
        }

        double ef = EMISSION_FACTORS.getOrDefault(activityType, 0.0);
        double estimatedEmissions = normalizedValue * ef;

        boolean suspiciousFlag = false;
        String suspiciousReason = "";
        if (quantity <= 0) {
            suspiciousFlag = true; suspiciousReason = "Non-positive quantity: " + quantity;
        } else if (normalizedValue > 50000) {
            suspiciousFlag = true;
            suspiciousReason = "Unusually high consumption: " + String.format("%.0f", normalizedValue) + " " + canonicalUnit;
        } else if (unitRaw.isEmpty() || "ST".equalsIgnoreCase(unitRaw)) {
            suspiciousFlag = true; suspiciousReason = "Missing or ambiguous unit (Stück/piece)";
        }

        LocalDate periodStart = parseDate(postingDate);

        return new NormalizationResult(
            activityType, "scope_1", unitRaw, quantity, canonicalUnit, normalizedValue,
            ef, estimatedEmissions, suspiciousFlag, suspiciousReason,
            periodStart, null, docNumber, warnings
        );
    }

    // ── Utility normalization ────────────────────────────────────────────────
    public NormalizationResult normalizeUtilityRecord(Map<String, String> row) {
        String meterId = row.getOrDefault("meter_id", "");
        String consumptionRaw = row.getOrDefault("consumption_kwh",
            row.getOrDefault("consumption", ""));
        String unitRaw = row.getOrDefault("unit", "kwh").strip();
        String billingStart = row.getOrDefault("billing_start", row.getOrDefault("period_start", ""));
        String billingEnd = row.getOrDefault("billing_end", row.getOrDefault("period_end", ""));
        String tariff = row.getOrDefault("tariff_type", "").toLowerCase();

        double consumption;
        try {
            consumption = Double.parseDouble(consumptionRaw.replace(",", ".").strip());
        } catch (NumberFormatException e) {
            throw new NormalizationException("Cannot parse consumption: '" + consumptionRaw + "'");
        }

        double normalizedValue;
        List<String> warnings = new ArrayList<>();
        try {
            normalizedValue = convertToCanonical(consumption, unitRaw)[0];
        } catch (NormalizationException e) {
            warnings.add(e.getMessage());
            normalizedValue = consumption;
        }

        double ef = EMISSION_FACTORS.get("electricity_consumption");
        double estimatedEmissions = normalizedValue * ef;

        boolean suspiciousFlag = false;
        String suspiciousReason = "";
        if (consumption <= 0) {
            suspiciousFlag = true; suspiciousReason = "Non-positive consumption: " + consumption;
        } else if (normalizedValue > 500000) {
            suspiciousFlag = true;
            suspiciousReason = "Extremely high consumption: " + String.format("%,.0f", normalizedValue) + " kWh — verify meter";
        }
        if (tariff.contains("renewable") || tariff.contains("green")) {
            warnings.add("Renewable/green tariff: market-based factor may differ from grid average");
        }

        return new NormalizationResult(
            "electricity_consumption", "scope_2", unitRaw, consumption, "kwh", normalizedValue,
            ef, estimatedEmissions, suspiciousFlag, suspiciousReason,
            parseDate(billingStart), parseDate(billingEnd), meterId, warnings
        );
    }

    // ── Travel normalization ─────────────────────────────────────────────────
    public NormalizationResult normalizeTravelRecord(Map<String, Object> row) {
        String bookingRef = String.valueOf(row.getOrDefault("booking_reference", ""));
        String modeRaw = String.valueOf(row.getOrDefault("travel_mode", "")).toLowerCase().strip();
        String origin = String.valueOf(row.getOrDefault("origin", "")).toUpperCase().strip();
        String destination = String.valueOf(row.getOrDefault("destination", "")).toUpperCase().strip();
        String travelClass = String.valueOf(row.getOrDefault("travel_class", "economy")).toLowerCase();
        Object distanceObj = row.getOrDefault("distance_km", null);
        Object nightsObj = row.getOrDefault("nights", 1);
        String tripDate = String.valueOf(row.getOrDefault("trip_date", row.getOrDefault("date", "")));

        String modeKey = TRAVEL_MODE_MAP.get(modeRaw);
        if (modeKey == null) {
            return new NormalizationResult(
                "unknown_travel", "scope_3", "n/a", 0.0, "n/a", 0.0, 0.0, 0.0,
                true, "Unrecognized travel mode: '" + modeRaw + "'",
                null, null, bookingRef, List.of()
            );
        }

        boolean suspiciousFlag = false;
        String suspiciousReason = "";
        List<String> warnings = new ArrayList<>();

        // FLIGHT
        if ("flight".equals(modeKey)) {
            if (!VALID_IATA.contains(origin)) {
                suspiciousFlag = true; suspiciousReason = "Unknown origin IATA code: '" + origin + "'";
            } else if (!VALID_IATA.contains(destination)) {
                suspiciousFlag = true; suspiciousReason = "Unknown destination IATA code: '" + destination + "'";
            }

            double distanceKm;
            if (distanceObj == null || toDouble(distanceObj) <= 0) {
                String key = origin + "-" + destination;
                Long dist = AIRPORT_DISTANCES.get(key);
                if (dist == null) {
                    suspiciousFlag = true;
                    suspiciousReason = "No distance data for " + origin + "→" + destination + "; used fallback 0 km";
                    distanceKm = 0.0;
                } else {
                    distanceKm = dist.doubleValue();
                    warnings.add("Distance imputed from lookup table: " + distanceKm + " km");
                }
            } else {
                distanceKm = toDouble(distanceObj);
            }

            boolean isLongHaul = distanceKm >= 3700;
            String efKey;
            if (travelClass.contains("business") || travelClass.contains("first")) {
                efKey = isLongHaul ? "flight_business_long" : "flight_business_short";
            } else {
                efKey = isLongHaul ? "flight_economy_long" : "flight_economy_short";
            }

            double ef = EMISSION_FACTORS.get(efKey);
            double estimatedEmissions = distanceKm * ef;
            String firstClass = travelClass.split(" ")[0];
            String actType = "flight_" + (isLongHaul ? "long" : "short") + "_" + firstClass;

            return new NormalizationResult(
                actType, "scope_3", "km", distanceKm, "passenger-km", distanceKm,
                ef, estimatedEmissions, suspiciousFlag, suspiciousReason,
                parseDate(tripDate), null, bookingRef, warnings
            );
        }

        // HOTEL
        if ("hotel_stay".equals(modeKey)) {
            double nights;
            try { nights = toDouble(nightsObj); } catch (Exception e) {
                nights = 1.0; warnings.add("Could not parse nights; defaulted to 1");
            }
            double ef = EMISSION_FACTORS.get("hotel_stay");
            if (nights <= 0) { suspiciousFlag = true; suspiciousReason = "Non-positive nights: " + nights; }
            else if (nights > 30) { suspiciousFlag = true; suspiciousReason = "Unusually long hotel stay: " + nights + " nights"; }

            return new NormalizationResult(
                "hotel_stay", "scope_3", "nights", nights, "room-nights", nights,
                ef, nights * ef, suspiciousFlag, suspiciousReason,
                parseDate(tripDate), null, bookingRef, warnings
            );
        }

        // GROUND
        double distanceKm;
        if (distanceObj == null || toDouble(distanceObj) <= 0) {
            suspiciousFlag = true; suspiciousReason = "Missing distance for ground transport";
            distanceKm = 0.0;
        } else {
            distanceKm = toDouble(distanceObj);
        }
        if (distanceKm > 1000) {
            suspiciousFlag = true;
            suspiciousReason = "Unusually long ground journey: " + String.format("%.0f", distanceKm) + " km — check travel mode";
        }

        double ef = EMISSION_FACTORS.getOrDefault(modeKey, EMISSION_FACTORS.get("ground_taxi"));
        return new NormalizationResult(
            modeKey, "scope_3", "km", distanceKm, "km", distanceKm,
            ef, distanceKm * ef, suspiciousFlag, suspiciousReason,
            parseDate(tripDate), null, bookingRef, warnings
        );
    }

    // ── Date parser ──────────────────────────────────────────────────────────
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    public LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        value = value.strip();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(value, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(obj));
    }
}
