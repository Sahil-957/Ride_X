package com.cabzen.entity;

import com.cabzen.enums.DocumentType;
import jakarta.persistence.*;

import java.time.Instant;

/** Uploaded file stored in Postgres (bytea), so it survives redeploys on hosts with ephemeral disks. */
@Entity
@Table(name = "driver_documents")
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    @Enumerated(EnumType.STRING)
    private DocumentType docType;

    private String fileName;
    private String contentType;
    private byte[] data;
    private Instant uploadedAt;

    protected DriverDocument() {
    }

    public DriverDocument(DriverProfile driver, DocumentType docType) {
        this.driver = driver;
        this.docType = docType;
    }

    public void replaceFile(String fileName, String contentType, byte[] data) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.data = data;
        this.uploadedAt = Instant.now();
    }

    public Long getId() { return id; }
    public DriverProfile getDriver() { return driver; }
    public DocumentType getDocType() { return docType; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getUploadedAt() { return uploadedAt; }
}
