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
public class NerResult {
    private List<Entity> entities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entity {
        private String type;
        private String value;
        private double confidence;
    }
}
