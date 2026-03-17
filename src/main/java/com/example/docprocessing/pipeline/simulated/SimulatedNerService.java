package com.example.docprocessing.pipeline.simulated;

import com.example.docprocessing.pipeline.NerService;
import com.example.docprocessing.pipeline.dto.NerResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.example.docprocessing.pipeline.simulated.Utils.shouldFail;
import static com.example.docprocessing.pipeline.simulated.Utils.sleepRandom;

@Service
public class SimulatedNerService implements NerService {

    @Override
    public NerResult extractEntities(String base64Content, UUID requestUid) {
        sleepRandom(2_000L, 6_000L);

        if (shouldFail(25)) {
            throw new RuntimeException("Simulated NER failure");
        }

        NerResult result = new NerResult();
        result.setEntities(List.of(
            entity("ORGANIZATION", "Acme Corp", 0.97),
            entity("DATE", "2026-01-15", 0.99),
            entity("AMOUNT", "$12,450.00", 0.95),
            entity("INVOICE_NUMBER", "INV-2026-0342", 0.98),
            entity("PERSON", "John Smith", 0.88)
        ));
        return result;
    }

    private static NerResult.Entity entity(String type, String value, double confidence) {
        NerResult.Entity e = new NerResult.Entity();
        e.setType(type);
        e.setValue(value);
        e.setConfidence(confidence);
        return e;
    }
}