package com.example.docprocessing.pipeline.simulated;

import com.example.docprocessing.pipeline.DmsClient;
import com.example.docprocessing.pipeline.dto.DmsDocumentMetadata;
import com.example.docprocessing.exception.DocumentTooLargeException;
import org.springframework.stereotype.Service;

import static com.example.docprocessing.pipeline.simulated.Utils.sleepRandom;
import static com.example.docprocessing.pipeline.simulated.Utils.shouldFail;


import java.util.Base64;
import java.util.Random;
import java.util.UUID;

@Service
public class SimulatedDmsClient implements DmsClient {

    private final Random random = new Random();

    @Override
    public DmsDocumentMetadata getMetadata(UUID docRef) {
        sleepRandom(500, 2000);

        if (shouldFail(10)) {
            throw new RuntimeException("Simulated DMS metadata failure");
        }

        long sizeBytes = generateRandomSizeBytes();

        long maxSize = 20L * 1024 * 1024;
        if (sizeBytes > maxSize) {
            throw new DocumentTooLargeException("DOCUMENT_TOO_LARGE");
        }

        DmsDocumentMetadata metadata = new DmsDocumentMetadata();
        metadata.setFileName("invoice_2026_q1.pdf");
        metadata.setContentType("application/pdf");
        metadata.setSizeBytes(sizeBytes);
        return metadata;
    }

    @Override
    public String getContentAsBase64(UUID docRef) {
        sleepRandom(500, 2000);

        if (shouldFail(10)) {
            throw new RuntimeException("Simulated DMS content fetch failure");
        }

        String fakeContent = "Simulated PDF content for docRef " + docRef;
        return Base64.getEncoder().encodeToString(fakeContent.getBytes());
    }

    private long generateRandomSizeBytes() {
        long min = 1L * 1024 * 1024;        // 1 MB
        long max = 30L * 1024 * 1024;       // 30 MB
        long range = max - min;
        long offset = Math.abs(random.nextLong()) % range;
        return min + offset;
    }
}