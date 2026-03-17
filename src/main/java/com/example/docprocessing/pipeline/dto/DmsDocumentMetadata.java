package com.example.docprocessing.pipeline.dto;

public class DmsDocumentMetadata {

    private String fileName;
    private String contentType;
    private long sizeBytes;
    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    public long getSizeBytes() {
        return sizeBytes;
    }
    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
    
}
