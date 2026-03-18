package com.example.docprocessing.pipeline.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsDocumentMetadata {
    private String fileName;
    private String contentType;
    private long sizeBytes;
}
