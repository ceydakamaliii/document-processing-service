package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import com.example.docprocessing.exception.DocumentTooLargeException;
import com.example.docprocessing.pipeline.*;
import com.example.docprocessing.pipeline.dto.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final WorkflowService workflowService;
    private final DmsClient dmsClient;
    private final OcrService ocrService;
    private final ClassifierService classifierService;
    private final NerService nerService;
    private final ObjectMapper objectMapper;
    private final StepExecutor stepExecutor;

    public PipelineOrchestrator(WorkflowService workflowService,
                               DmsClient dmsClient,
                               OcrService ocrService,
                               ClassifierService classifierService,
                               NerService nerService,
                               ObjectMapper objectMapper,
                               StepExecutor stepExecutor) {
        this.workflowService = workflowService;
        this.dmsClient = dmsClient;
        this.ocrService = ocrService;
        this.classifierService = classifierService;
        this.nerService = nerService;
        this.objectMapper = objectMapper;
        this.stepExecutor = stepExecutor;
    }

    @Async("taskExecutor")
    public void startProcessing(UUID documentId) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        WorkflowStatus currentStep = wf.getCurrentStep();
        UUID requestUid = wf.getRequestUid();

        switch (currentStep) {
            case RECEIVED:
                workflowService.transitionTo(documentId, WorkflowStatus.DMS_FETCHING);
                executeDmsFetch(documentId);
                break;
            case DMS_FETCHING:
                executeDmsFetch(documentId);
                break;
            case DMS_FETCH_COMPLETED:
            case OCR_PROCESSING: {
                String base64 = getBase64FromDmsStep(documentId);
                executeOcr(documentId, base64, requestUid);
                break;
            }
            case OCR_COMPLETED:
            case CLASSIFYING: {
                String base64 = getBase64FromDmsStep(documentId);
                executeClassification(documentId, base64, requestUid);
                break;
            }
            case CLASSIFICATION_COMPLETED:
            case NER_PROCESSING: {
                String base64 = getBase64FromDmsStep(documentId);
                executeNer(documentId, base64, requestUid);
                break;
            }
            case COMPLETED:
            case FAILED:
            case DMS_FETCH_FAILED:
            case OCR_FAILED:
            case CLASSIFICATION_FAILED:
            case NER_FAILED:
                log.debug("startProcessing no-op for documentId={} in state {}", documentId, currentStep);
                break;
            case NER_COMPLETED:
                workflowService.transitionTo(documentId, WorkflowStatus.COMPLETED);
                break;
            default:
                log.warn("Unexpected currentStep {} for documentId={}, starting from DMS", currentStep, documentId);
                workflowService.transitionTo(documentId, WorkflowStatus.DMS_FETCHING);
                executeDmsFetch(documentId);
        }
    }

    private String getBase64FromDmsStep(UUID documentId) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        return workflowService.getStepResult(documentId, ProcessingStep.DMS_FETCH)
            .filter(sr -> sr.getStatus() == StepStatus.COMPLETED)
            .map(sr -> {
                String json = sr.getResultJson();
                if (json == null) return null;
                try {
                    Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                    Object content = map.get("contentBase64");
                    return content != null ? content.toString() : null;
                } catch (Exception e) {
                    log.warn("Failed to parse DMS resultJson for documentId={}", documentId, e);
                    return null;
                }
            })
            .filter(base64 -> base64 != null && !base64.isEmpty())
            .orElseGet(() -> {
                log.info("No contentBase64 in DMS result for documentId={}, re-fetching from DMS", documentId);
                return dmsClient.getContentAsBase64(wf.getDocRef());
            });
    }

    private void executeDmsFetch(UUID documentId) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        UUID docRef = wf.getDocRef();
        UUID requestUid = wf.getRequestUid();

        final DmsDocumentMetadata[] metadataHolder = new DmsDocumentMetadata[1];

        stepExecutor.executeStep(documentId,
            WorkflowStatus.DMS_FETCHING,
            WorkflowStatus.DMS_FETCH_COMPLETED,
            WorkflowStatus.DMS_FETCH_FAILED,
            ProcessingStep.DMS_FETCH,
            () -> {
                DmsDocumentMetadata metadata = dmsClient.getMetadata(docRef);
                metadataHolder[0] = metadata;
                
                if (metadata.getSizeBytes() > 20L * 1024 * 1024) {
                    throw new DocumentTooLargeException("DOCUMENT_TOO_LARGE");
                }
                
                String base64 = dmsClient.getContentAsBase64(docRef);
                
                Map<String, Object> dmsResult = new HashMap<>();
                dmsResult.put("fileName", metadata.getFileName());
                dmsResult.put("contentType", metadata.getContentType());
                dmsResult.put("sizeBytes", metadata.getSizeBytes());
                
                Map<String, Object> fullResult = new HashMap<>();
                fullResult.putAll(dmsResult);
                fullResult.put("contentBase64", base64);
                
                return toJson(fullResult);
            });

        if (workflowService.getById(documentId).getCurrentStep() == WorkflowStatus.DMS_FETCH_COMPLETED 
            && metadataHolder[0] != null) {
            workflowService.updateDocumentMetadata(
                documentId, 
                metadataHolder[0].getFileName(), 
                metadataHolder[0].getSizeBytes()
            );
        }

        if (workflowService.getById(documentId).getCurrentStep() == WorkflowStatus.DMS_FETCH_COMPLETED) {
            executeOcr(documentId, getBase64FromDmsStep(documentId), requestUid);
        }
    }

    private void executeOcr(UUID documentId, String base64Content, UUID requestUid) {
        stepExecutor.executeStep(documentId,
            WorkflowStatus.OCR_PROCESSING,
            WorkflowStatus.OCR_COMPLETED,
            WorkflowStatus.OCR_FAILED,
            ProcessingStep.OCR,
            () -> toJson(ocrService.process(base64Content, requestUid)));

        if (workflowService.getById(documentId).getCurrentStep() == WorkflowStatus.OCR_COMPLETED) {
            executeClassification(documentId, base64Content, requestUid);
        }
    }

    private void executeClassification(UUID documentId, String base64Content, UUID requestUid) {
        stepExecutor.executeStep(documentId,
            WorkflowStatus.CLASSIFYING,
            WorkflowStatus.CLASSIFICATION_COMPLETED,
            WorkflowStatus.CLASSIFICATION_FAILED,
            ProcessingStep.CLASSIFICATION,
            () -> toJson(classifierService.classify(base64Content, requestUid)));

        if (workflowService.getById(documentId).getCurrentStep() == WorkflowStatus.CLASSIFICATION_COMPLETED) {
            executeNer(documentId, base64Content, requestUid);
        }
    }

    private void executeNer(UUID documentId, String base64Content, UUID requestUid) {
        stepExecutor.executeStep(documentId,
            WorkflowStatus.NER_PROCESSING,
            WorkflowStatus.NER_COMPLETED,
            WorkflowStatus.NER_FAILED,
            ProcessingStep.NER,
            () -> toJson(nerService.extractEntities(base64Content, requestUid)));

        if (workflowService.getById(documentId).getCurrentStep() == WorkflowStatus.NER_COMPLETED) {
            workflowService.transitionTo(documentId, WorkflowStatus.COMPLETED);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}