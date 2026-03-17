package com.example.docprocessing.pipeline;

import com.example.docprocessing.pipeline.dto.DmsDocumentMetadata;

import java.util.UUID;

public interface DmsClient {

    DmsDocumentMetadata getMetadata(UUID docRef);

    String getContentAsBase64(UUID docRef);
}