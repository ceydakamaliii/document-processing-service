package com.example.docprocessing.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentSummaryResponse {
    private UUID documentId;
    private UUID docRef;
    private String documentName;
    private String currentStep;
    private String failedAtStep;
    private Instant createdAt;
    private Instant updatedAt;
}
