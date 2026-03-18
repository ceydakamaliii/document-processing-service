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
public class CancelDocumentResponse {
    private UUID documentId;
    private String previousStep;
    private String currentStep;
    private String reason;
    private Instant cancelledAt;
}
