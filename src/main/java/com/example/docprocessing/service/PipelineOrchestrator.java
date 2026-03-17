package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import com.example.docprocessing.exception.DocumentTooLargeException;
import com.example.docprocessing.pipeline.*;
import com.example.docprocessing.pipeline.dto.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    @Async
    public void startProcessing(UUID documentId) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        workflowService.transitionTo(documentId, WorkflowStatus.DMS_FETCHING);
        executeDmsFetch(documentId);
    }

    private void executeDmsFetch(UUID documentId) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        UUID docRef = wf.getDocRef();
        UUID requestUid = wf.getRequestUid();
        Instant startedAt = Instant.now();

        workflowService.saveStepResult(documentId, ProcessingStep.DMS_FETCH, StepStatus.PROCESSING,
            startedAt, null, null, null, null);

        try {
            DmsDocumentMetadata metadata = dmsClient.getMetadata(docRef);
            if (metadata.getSizeBytes() > 20L * 1024 * 1024) {
                throw new DocumentTooLargeException("Document exceeds 20MB");
            }
            String base64 = dmsClient.getContentAsBase64(docRef);
            long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            String resultJson = objectMapper.writeValueAsString(metadata);

            workflowService.saveStepResult(documentId, ProcessingStep.DMS_FETCH, StepStatus.COMPLETED,
                startedAt, Instant.now(), durationMs, resultJson, null);
            workflowService.transitionTo(documentId, WorkflowStatus.DMS_FETCH_COMPLETED);

            executeOcr(documentId, base64, requestUid);

        } catch (DocumentTooLargeException e) {
            failStep(documentId, ProcessingStep.DMS_FETCH, startedAt, WorkflowStatus.DMS_FETCH_FAILED, e.getMessage());
        } catch (Exception e) {
            log.warn("DMS fetch failed for documentId={}", documentId, e);
            failStep(documentId, ProcessingStep.DMS_FETCH, startedAt, WorkflowStatus.DMS_FETCH_FAILED, e.getMessage());
        }
    }

    private void executeOcr(UUID documentId, String base64Content, UUID requestUid) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        workflowService.transitionTo(documentId, WorkflowStatus.OCR_PROCESSING);
        Instant startedAt = Instant.now();

        workflowService.saveStepResult(documentId, ProcessingStep.OCR, StepStatus.PROCESSING,
            startedAt, null, null, null, null);

        try {
            OcrResult result = ocrService.process(base64Content, requestUid);
            long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            String resultJson = objectMapper.writeValueAsString(result);

            workflowService.saveStepResult(documentId, ProcessingStep.OCR, StepStatus.COMPLETED,
                startedAt, Instant.now(), durationMs, resultJson, null);
            workflowService.transitionTo(documentId, WorkflowStatus.OCR_COMPLETED);

            executeClassification(documentId, base64Content, requestUid);

        } catch (Exception e) {
            log.warn("OCR failed for documentId={}", documentId, e);
            failStep(documentId, ProcessingStep.OCR, startedAt, WorkflowStatus.OCR_FAILED, e.getMessage());
        }
    }

    private void executeClassification(UUID documentId, String base64Content, UUID requestUid) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        workflowService.transitionTo(documentId, WorkflowStatus.CLASSIFYING);
        Instant startedAt = Instant.now();

        workflowService.saveStepResult(documentId, ProcessingStep.CLASSIFICATION, StepStatus.PROCESSING,
            startedAt, null, null, null, null);

        try {
            ClassificationResult result = classifierService.classify(base64Content, requestUid);
            long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            String resultJson = objectMapper.writeValueAsString(result);

            workflowService.saveStepResult(documentId, ProcessingStep.CLASSIFICATION, StepStatus.COMPLETED,
                startedAt, Instant.now(), durationMs, resultJson, null);
            workflowService.transitionTo(documentId, WorkflowStatus.CLASSIFICATION_COMPLETED);

            executeNer(documentId, base64Content, requestUid);

        } catch (Exception e) {
            log.warn("Classification failed for documentId={}", documentId, e);
            failStep(documentId, ProcessingStep.CLASSIFICATION, startedAt, WorkflowStatus.CLASSIFICATION_FAILED, e.getMessage());
        }
    }

    private void executeNer(UUID documentId, String base64Content, UUID requestUid) {
        DocumentWorkflow wf = workflowService.getById(documentId);
        workflowService.transitionTo(documentId, WorkflowStatus.NER_PROCESSING);
        Instant startedAt = Instant.now();

        workflowService.saveStepResult(documentId, ProcessingStep.NER, StepStatus.PROCESSING,
            startedAt, null, null, null, null);

        try {
            NerResult result = nerService.extractEntities(base64Content, requestUid);
            long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
            String resultJson = objectMapper.writeValueAsString(result);

            workflowService.saveStepResult(documentId, ProcessingStep.NER, StepStatus.COMPLETED,
                startedAt, Instant.now(), durationMs, resultJson, null);
            workflowService.transitionTo(documentId, WorkflowStatus.NER_COMPLETED);
            workflowService.transitionTo(documentId, WorkflowStatus.COMPLETED);

        } catch (Exception e) {
            log.warn("NER failed for documentId={}", documentId, e);
            failStep(documentId, ProcessingStep.NER, startedAt, WorkflowStatus.NER_FAILED, e.getMessage());
        }
    }

    private void failStep(UUID documentId, ProcessingStep step, Instant startedAt,
                         WorkflowStatus failedStatus, String errorMessage) {
        long durationMs = java.time.Duration.between(startedAt, Instant.now()).toMillis();
        workflowService.saveStepResult(documentId, step, StepStatus.FAILED,
            startedAt, Instant.now(), durationMs, null, errorMessage);
        workflowService.transitionTo(documentId, failedStatus);
        workflowService.transitionTo(documentId, WorkflowStatus.FAILED);
    }
}