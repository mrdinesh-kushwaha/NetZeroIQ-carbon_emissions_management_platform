package com.carbonlens.repository;
import com.carbonlens.model.RawRecord;
import com.carbonlens.model.UploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RawRecordRepository extends JpaRepository<RawRecord, UUID> {}
