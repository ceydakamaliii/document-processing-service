package com.example.docprocessing.pipeline.dto;

import java.util.List;

public class ClassificationResult {

    private String documentType;
    private double confidence;
    private List<AlternativeType> alternativeTypes;

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<AlternativeType> getAlternativeTypes() {
        return alternativeTypes;
    }

    public void setAlternativeTypes(List<AlternativeType> alternativeTypes) {
        this.alternativeTypes = alternativeTypes;
    }

    public static class AlternativeType {

        private String type;
        private double confidence;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }
}