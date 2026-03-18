package com.example.docprocessing.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepResultDto {
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private JsonNode result;
    private String errorMessage;
}
