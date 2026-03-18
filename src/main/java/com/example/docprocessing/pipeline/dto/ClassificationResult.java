package com.example.docprocessing.pipeline.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResult {
    private String documentType;
    private double confidence;
    private List<AlternativeType> alternativeTypes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativeType {
        private String type;
        private double confidence;
    }
}
