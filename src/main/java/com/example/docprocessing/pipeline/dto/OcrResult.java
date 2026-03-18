package com.example.docprocessing.pipeline.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    private int pageCount;
    private int wordCount;
    private double confidence;
    private String rawText;
}
