package com.netzeroiq.config;

import com.netzeroiq.model.DataSource;
import com.netzeroiq.model.Tenant;
import com.netzeroiq.model.UploadBatch;
import com.netzeroiq.model.User;
import com.netzeroiq.repository.DataSourceRepository;
import com.netzeroiq.repository.TenantRepository;
import com.netzeroiq.repository.UploadBatchRepository;
import com.netzeroiq.repository.UserRepository;
import com.netzeroiq.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds local/demo data when enabled by profile or environment.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.analyst-password:analyst@1234}")
    private String analystPassword;

    @Value("${app.seed.reviewer-password:reviewer@1234}")
    private String reviewerPassword;

    @Value("${app.seed.admin-password:admin@1234}")
    private String adminPassword;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final DataSourceRepository dataSourceRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final IngestionService ingestionService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Seed data initialization is disabled for this profile.");
            return;
        }

        log.info("Verifying netzeroiq seed data...");

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> "netzeroiq".equalsIgnoreCase(t.getSlug()))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("netzeroiq Workspace")
                        .slug("netzeroiq")
                        .industry("Sustainability / Emissions Intelligence")
                        .build()));

        User analyst = upsertDemoUser("analyst@netzeroiq.com", "Raj", "Analyst", User.Role.analyst, false, tenant, analystPassword);
        upsertDemoUser("reviewer@netzeroiq.com", "Amrit", "Reviewer", User.Role.reviewer, false, tenant, reviewerPassword);
        upsertDemoUser("dinesh@netzeroiq.com", "Dinesh Kushwaha", "Admin", User.Role.admin, true, tenant, adminPassword);

        DataSource sapSource = seedDataSource(tenant, "SAP Production Export", DataSource.SourceType.sap_export, "Monthly fuel and procurement CSV exports.");
        DataSource utilitySource = seedDataSource(tenant, "Utility Billing Portal", DataSource.SourceType.utility_portal, "Electricity and energy billing CSV imports.");
        DataSource travelSource = seedDataSource(tenant, "Travel API Feed", DataSource.SourceType.travel_api, "Business travel JSON payload imports.");

        seedSampleUploadsIfEmpty(tenant, analyst, sapSource, utilitySource, travelSource);

        log.info("netzeroiq seed data verified successfully.");
    }

    private User upsertDemoUser(String email, String firstName, String lastName, User.Role role, boolean staff, Tenant tenant, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStaff(staff);
        user.setActive(true);
        user.setTenant(tenant);
        User saved = userRepository.save(user);
        log.info("Seed user verified: {} ({})", email, role);
        return saved;
    }

    private DataSource seedDataSource(Tenant tenant, String name, DataSource.SourceType type, String description) {
        return dataSourceRepository.findByTenant(tenant).stream()
                .filter(ds -> ds.getSourceType() == type)
                .findFirst()
                .orElseGet(() -> {
                    DataSource saved = dataSourceRepository.save(DataSource.builder()
                            .tenant(tenant)
                            .name(name)
                            .sourceType(type)
                            .description(description)
                            .build());
                    log.info("Data source seeded: {}", name);
                    return saved;
                });
    }

    private void seedSampleUploadsIfEmpty(Tenant tenant, User analyst, DataSource sapSource,
                                          DataSource utilitySource, DataSource travelSource) {
        if (uploadBatchRepository.countByTenant(tenant) > 0) {
            log.info("Existing upload batches found. Sample data auto-load skipped.");
            return;
        }

        log.info("Loading sample_data into dashboard/review/audit...");
        ingestSampleFile(tenant, analyst, sapSource, "sap_sample.csv", DataSource.SourceType.sap_export);
        ingestSampleFile(tenant, analyst, utilitySource, "utility_sample.csv", DataSource.SourceType.utility_portal);
        ingestSampleFile(tenant, analyst, travelSource, "travel_sample.json", DataSource.SourceType.travel_api);
    }

    private void ingestSampleFile(Tenant tenant, User analyst, DataSource dataSource,
                                  String filename, DataSource.SourceType sourceType) {
        try {
            byte[] bytes = readSampleData(filename);
            UploadBatch batch = UploadBatch.builder()
                    .tenant(tenant)
                    .dataSource(dataSource)
                    .uploadedBy(analyst)
                    .originalFilename(filename)
                    .status(UploadBatch.Status.processing)
                    .build();
            uploadBatchRepository.save(batch);

            if (sourceType == DataSource.SourceType.sap_export) {
                ingestionService.ingestSapCsv(bytes, batch, analyst);
            } else if (sourceType == DataSource.SourceType.utility_portal) {
                ingestionService.ingestUtilityCsv(bytes, batch, analyst);
            } else {
                ingestionService.ingestTravelJson(bytes, batch, analyst);
            }
            log.info("Sample loaded: sample_data/{}", filename);
        } catch (Exception ex) {
            log.warn("Sample auto-load skipped for {}: {}", filename, ex.getMessage());
        }
    }

    private byte[] readSampleData(String filename) throws Exception {
    try (var inputStream = getClass().getClassLoader()
            .getResourceAsStream("sample_data/" + filename)) {

        if (inputStream == null) {
            throw new IllegalStateException("sample_data/" + filename + " not found in classpath.");
        }

        return inputStream.readAllBytes();
        }
    }
}
