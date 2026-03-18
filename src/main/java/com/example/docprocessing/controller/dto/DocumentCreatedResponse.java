package com.example.docprocessing.controller.dto;

import com.example.docprocessing.domain.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentCreatedResponse(
    UUID documentId,
    UUID docRef,
    UUID requestUid,
    WorkflowStatus currentStep,
    Instant createdAt
) {}
