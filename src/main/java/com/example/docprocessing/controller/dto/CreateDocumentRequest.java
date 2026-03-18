package com.example.docprocessing.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDocumentRequest(
    @NotNull(message = "docRef is required")
    UUID docRef
) {}
