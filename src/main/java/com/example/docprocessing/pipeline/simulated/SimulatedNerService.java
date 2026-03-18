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

        return NerResult.builder()
            .entities(List.of(
                NerResult.Entity.builder()
                    .type("ORGANIZATION")
                    .value("Acme Corp")
                    .confidence(0.97)
                    .build(),
                NerResult.Entity.builder()
                    .type("DATE")
                    .value("2026-01-15")
                    .confidence(0.99)
                    .build(),
                NerResult.Entity.builder()
                    .type("AMOUNT")
                    .value("$12,450.00")
                    .confidence(0.95)
                    .build(),
                NerResult.Entity.builder()
                    .type("INVOICE_NUMBER")
                    .value("INV-2026-0342")
                    .confidence(0.98)
                    .build(),
                NerResult.Entity.builder()
                    .type("PERSON")
                    .value("John Smith")
                    .confidence(0.88)
                    .build()
            ))
            .build();
    }
}