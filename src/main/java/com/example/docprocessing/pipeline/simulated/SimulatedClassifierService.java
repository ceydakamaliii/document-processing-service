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

        ClassificationResult result = new ClassificationResult();
        result.setDocumentType("INVOICE");
        result.setConfidence(0.94);
        result.setAlternativeTypes(List.of(
            createAlternative("PURCHASE_ORDER", 0.04),
            createAlternative("RECEIPT", 0.02)
        ));
        return result;
    }

    private static ClassificationResult.AlternativeType createAlternative(String type, double confidence) {
        ClassificationResult.AlternativeType alt = new ClassificationResult.AlternativeType();
        alt.setType(type);
        alt.setConfidence(confidence);
        return alt;
    }
}