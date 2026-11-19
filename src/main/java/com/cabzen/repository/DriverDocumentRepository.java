package com.cabzen.repository;

import com.cabzen.entity.DriverDocument;
import com.cabzen.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    Optional<DriverDocument> findByDriver_UserIdAndDocType(Long driverId, DocumentType docType);

    /** Metadata only: the projection keeps the file bytes out of list queries. */
    List<DocumentSummary> findByDriver_UserIdOrderByDocType(Long driverId);

    long countByDriver_UserId(Long driverId);

    interface DocumentSummary {
        Long getId();
        DocumentType getDocType();
        String getFileName();
        String getContentType();
        Instant getUploadedAt();
    }
}
