package com.example.docprocessing.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentStatusResponse {
    private UUID documentId;
    private UUID docRef;
    private UUID requestUid;
    private String documentName;
    private Long contentSizeBytes;
    private String currentStep;
    private String failedAtStep;
    private String failureReason;
    private Integer retryCount;
    private Map<String, StepResultDto> stepResults;
    private Instant createdAt;
    private Instant updatedAt;
}
