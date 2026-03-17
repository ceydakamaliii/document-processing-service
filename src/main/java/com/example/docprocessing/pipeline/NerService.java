package com.example.docprocessing.pipeline;

import com.example.docprocessing.pipeline.dto.NerResult;
import java.util.UUID;

public interface NerService {

    NerResult extractEntities(String base64Content, UUID requestUid);
}