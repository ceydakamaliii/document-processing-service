package com.example.docprocessing.pipeline.simulated;

import com.example.docprocessing.pipeline.OcrService;
import com.example.docprocessing.pipeline.dto.OcrResult;
import org.springframework.stereotype.Service;

import static com.example.docprocessing.pipeline.simulated.Utils.sleepRandom;
import static com.example.docprocessing.pipeline.simulated.Utils.shouldFail;
import java.util.UUID;

@Service
public class SimulatedOcrService implements OcrService {

    @Override
    public OcrResult process(String base64Content, UUID requestUid) {
        sleepRandom(2_000L, 8_000L);

        if (shouldFail(20)) {
            throw new RuntimeException("Simulated OCR failure");
        }

        return OcrResult.builder()
            .pageCount(3)
            .wordCount(847)
            .confidence(0.96)
            .rawText("""
                Invoice #INV-2026-0342
                Date: 2026-01-15
                Bill To: Acme Corp...
                """)
            .build();
    }
}