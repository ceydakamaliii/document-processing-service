package com.example.docprocessing.pipeline.simulated;

import com.example.docprocessing.pipeline.ClassifierService;
import com.example.docprocessing.pipeline.dto.ClassificationResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.example.docprocessing.pipeline.simulated.Utils.shouldFail;
import static com.example.docprocessing.pipeline.simulated.Utils.sleepRandom;

@Service
public class SimulatedClassifierService implements ClassifierService {

    @Override
    public ClassificationResult classify(String base64Content, UUID requestUid) {
        sleepRandom(1_000L, 4_000L);

        if (shouldFail(15)) {
            throw new RuntimeException("Simulated Classifier failure");
        }

        return ClassificationResult.builder()
            .documentType("INVOICE")
            .confidence(0.94)
            .alternativeTypes(List.of(
                ClassificationResult.AlternativeType.builder()
                    .type("PURCHASE_ORDER")
                    .confidence(0.04)
                    .build(),
                ClassificationResult.AlternativeType.builder()
                    .type("RECEIPT")
                    .confidence(0.02)
                    .build()
            ))
            .build();
    }
}