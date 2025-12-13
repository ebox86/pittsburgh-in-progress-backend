package com.pip.projectlink.model;

import com.pip.ingest.model.ProjectCandidateEvent;

public class SourceInfo {

    private String pdfBucket;
    private String pdfObject;
    private String pdfGcsPath;
    private Long pdfSizeBytes;
    private String originalUrl;

    public SourceInfo() {
    }

    public String getPdfBucket() {
        return pdfBucket;
    }

    public void setPdfBucket(String pdfBucket) {
        this.pdfBucket = pdfBucket;
    }

    public String getPdfObject() {
        return pdfObject;
    }

    public void setPdfObject(String pdfObject) {
        this.pdfObject = pdfObject;
    }

    public String getPdfGcsPath() {
        return pdfGcsPath;
    }

    public void setPdfGcsPath(String pdfGcsPath) {
        this.pdfGcsPath = pdfGcsPath;
    }

    public Long getPdfSizeBytes() {
        return pdfSizeBytes;
    }

    public void setPdfSizeBytes(Long pdfSizeBytes) {
        this.pdfSizeBytes = pdfSizeBytes;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public static SourceInfo fromCandidate(ProjectCandidateEvent candidate) {
        SourceInfo source = new SourceInfo();
        source.setPdfBucket(candidate.bucket());
        source.setPdfObject(candidate.objectName());
        source.setPdfSizeBytes(candidate.sizeBytes());
        source.setOriginalUrl(candidate.originalUrl());
        if (candidate.bucket() != null && candidate.objectName() != null) {
            source.setPdfGcsPath("gs://" + candidate.bucket() + "/" + candidate.objectName());
        }
        return source;
    }
}
