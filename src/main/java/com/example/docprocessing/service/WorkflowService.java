package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import com.example.docprocessing.exception.DocumentNotFoundException;
import com.example.docprocessing.exception.InvalidTransitionException;
import com.example.docprocessing.repository.DocumentWorkflowRepository;
import com.example.docprocessing.repository.StepResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowService {

    private static final Logger transitionLog = LoggerFactory.getLogger("workflow.transition");

    private final DocumentWorkflowRepository documentWorkflowRepository;
    private final StepResultRepository stepResultRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(DocumentWorkflowRepository documentWorkflowRepository,
                           StepResultRepository stepResultRepository,
                           ObjectMapper objectMapper) {
        this.documentWorkflowRepository = documentWorkflowRepository;
        this.stepResultRepository = stepResultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentWorkflow createWorkflow(UUID docRef, String documentName, Long contentSizeBytes) {
        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setId(UUID.randomUUID());
        wf.setDocRef(docRef);
        wf.setRequestUid(UUID.randomUUID());
        wf.setDocumentName(documentName);
        wf.setContentSizeBytes(contentSizeBytes);
        wf.setCurrentStep(WorkflowStatus.RECEIVED);
        wf.setRetryCount(0);
        Instant now = Instant.now();
        wf.setCreatedAt(now);
        wf.setUpdatedAt(now);
        return documentWorkflowRepository.save(wf);
    }

    @Transactional(readOnly = true)
    public DocumentWorkflow getById(UUID documentId) {
        return documentWorkflowRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
    }

    @Transactional
    public DocumentWorkflow transitionTo(UUID documentId, WorkflowStatus newStatus) {
        return transitionTo(documentId, newStatus, null);
    }

    @Transactional
    public DocumentWorkflow transitionTo(UUID documentId, WorkflowStatus newStatus, String errorReason) {
        DocumentWorkflow wf = getById(documentId);
        WorkflowStatus previous = wf.getCurrentStep();

        if (!isValidTransition(previous, newStatus)) {
            throw new InvalidTransitionException("Invalid transition: " + previous + " -> " + newStatus);
        }

        long durationMs = wf.getUpdatedAt() != null
            ? Duration.between(wf.getUpdatedAt(), Instant.now()).toMillis()
            : 0;

        wf.setCurrentStep(newStatus);
        if (isFailureStatus(newStatus)) {
            wf.setFailedAtStep(previous);
            if (errorReason != null) {
                wf.setFailureReason(errorReason);
            }
        }
        wf.setUpdatedAt(Instant.now());
        DocumentWorkflow saved = documentWorkflowRepository.save(wf);

        logTransition(documentId, wf.getRequestUid(), previous, newStatus, durationMs,
            "State transition", errorReason);
        return saved;
    }

    private boolean isValidTransition(WorkflowStatus from, WorkflowStatus to) {
        switch (from) {
            case RECEIVED:
                return to == WorkflowStatus.DMS_FETCHING;

            case DMS_FETCHING:
                return EnumSet.of(WorkflowStatus.DMS_FETCH_COMPLETED, WorkflowStatus.DMS_FETCH_FAILED).contains(to);

            case DMS_FETCH_COMPLETED:
                return to == WorkflowStatus.OCR_PROCESSING;

            case OCR_PROCESSING:
                return EnumSet.of(WorkflowStatus.OCR_COMPLETED, WorkflowStatus.OCR_FAILED).contains(to);

            case OCR_COMPLETED:
                return to == WorkflowStatus.CLASSIFYING;

            case CLASSIFYING:
                return EnumSet.of(WorkflowStatus.CLASSIFICATION_COMPLETED, WorkflowStatus.CLASSIFICATION_FAILED).contains(to);

            case CLASSIFICATION_COMPLETED:
                return to == WorkflowStatus.NER_PROCESSING;

            case NER_PROCESSING:
                return EnumSet.of(WorkflowStatus.NER_COMPLETED, WorkflowStatus.NER_FAILED).contains(to);

            case NER_COMPLETED:
                return to == WorkflowStatus.COMPLETED;

            case DMS_FETCH_FAILED:
            case OCR_FAILED:
            case CLASSIFICATION_FAILED:
            case NER_FAILED:
                return to == WorkflowStatus.FAILED;

            case FAILED:
            case COMPLETED:
                return false;

            default:
                return false;
        }
    }

    private boolean isFailureStatus(WorkflowStatus status) {
        return EnumSet.of(
            WorkflowStatus.DMS_FETCH_FAILED,
            WorkflowStatus.OCR_FAILED,
            WorkflowStatus.CLASSIFICATION_FAILED,
            WorkflowStatus.NER_FAILED,
            WorkflowStatus.FAILED
        ).contains(status);
    }

    @Transactional
    public DocumentWorkflow retry(UUID documentId) {
        DocumentWorkflow wf = getById(documentId);

        if (wf.getCurrentStep() != WorkflowStatus.FAILED) {
            throw new InvalidTransitionException("Retry allowed only when workflow is in FAILED state");
        }

        wf.setRetryCount(wf.getRetryCount() + 1);
        WorkflowStatus restartFrom = wf.getFailedAtStep();

        if (restartFrom == null) {
            wf.setCurrentStep(WorkflowStatus.DMS_FETCHING);
        } else {
            switch (restartFrom) {
                case DMS_FETCHING:
                case DMS_FETCH_FAILED:
                    wf.setCurrentStep(WorkflowStatus.DMS_FETCHING);
                    break;
                case OCR_PROCESSING:
                case OCR_FAILED:
                    wf.setCurrentStep(WorkflowStatus.OCR_PROCESSING);
                    break;
                case CLASSIFYING:
                case CLASSIFICATION_FAILED:
                    wf.setCurrentStep(WorkflowStatus.CLASSIFYING);
                    break;
                case NER_PROCESSING:
                case NER_FAILED:
                    wf.setCurrentStep(WorkflowStatus.NER_PROCESSING);
                    break;
                default:
                    wf.setCurrentStep(WorkflowStatus.DMS_FETCHING);
            }
        }

        Instant now = Instant.now();
        long durationMs = wf.getUpdatedAt() != null
            ? Duration.between(wf.getUpdatedAt(), now).toMillis()
            : 0;
        wf.setUpdatedAt(now);
        DocumentWorkflow saved = documentWorkflowRepository.save(wf);
        logTransition(documentId, wf.getRequestUid(), WorkflowStatus.FAILED, saved.getCurrentStep(),
            durationMs, "Retry - resuming from failed step", null);
        return saved;
    }

    @Transactional
    public DocumentWorkflow cancel(UUID documentId) {
        DocumentWorkflow wf = getById(documentId);

        if (wf.getCurrentStep() == WorkflowStatus.COMPLETED || wf.getCurrentStep() == WorkflowStatus.FAILED) {
            throw new InvalidTransitionException("Cannot cancel a terminal workflow: " + wf.getCurrentStep());
        }

        WorkflowStatus previous = wf.getCurrentStep();
        wf.setFailedAtStep(previous);
        wf.setCurrentStep(WorkflowStatus.FAILED);
        wf.setFailureReason("CANCELLED_BY_USER");
        Instant now = Instant.now();
        long durationMs = wf.getUpdatedAt() != null
            ? Duration.between(wf.getUpdatedAt(), now).toMillis()
            : 0;
        wf.setUpdatedAt(now);
        DocumentWorkflow saved = documentWorkflowRepository.save(wf);
        logTransition(documentId, wf.getRequestUid(), previous, WorkflowStatus.FAILED,
            durationMs, "Cancelled by user", "CANCELLED_BY_USER");
        return saved;
    }

    @Transactional
    public StepResult saveStepResult(UUID documentId,
                                     ProcessingStep step,
                                     StepStatus status,
                                     Instant startedAt,
                                     Instant completedAt,
                                     Long durationMs,
                                     String resultJson,
                                     String errorMessage) {

        DocumentWorkflow wf = getById(documentId);

        StepResult stepResult = stepResultRepository
            .findByDocumentWorkflow_IdAndStep(documentId, step)
            .orElseGet(() -> {
                StepResult sr = new StepResult();
                sr.setDocumentWorkflow(wf);
                sr.setStep(step);
                return sr;
            });

        stepResult.setStatus(status);
        stepResult.setStartedAt(startedAt);
        stepResult.setCompletedAt(completedAt);
        stepResult.setDurationMs(durationMs);
        stepResult.setResultJson(resultJson);
        stepResult.setErrorMessage(errorMessage);

        return stepResultRepository.save(stepResult);
    }

    @Transactional(readOnly = true)
    public Optional<StepResult> getStepResult(UUID documentId, ProcessingStep step) {
        return stepResultRepository.findByDocumentWorkflow_IdAndStep(documentId, step);
    }

    @Transactional(readOnly = true)
    public Map<ProcessingStep, StepResult> getAllStepResults(UUID documentId) {
        var results = stepResultRepository.findByDocumentWorkflow_Id(documentId);
        Map<ProcessingStep, StepResult> map = new HashMap<>();
        for (StepResult result : results) {
            map.put(result.getStep(), result);
        }
        return map;
    }

    @Transactional
    public void updateDocumentMetadata(UUID documentId, String documentName, Long contentSizeBytes) {
        DocumentWorkflow wf = getById(documentId);
        if (documentName != null) {
            wf.setDocumentName(documentName);
        }
        if (contentSizeBytes != null) {
            wf.setContentSizeBytes(contentSizeBytes);
        }
        wf.setUpdatedAt(Instant.now());
        documentWorkflowRepository.save(wf);
    }

    @Transactional(readOnly = true)
    public List<DocumentWorkflow> listDocuments(WorkflowStatus status) {
        if (status != null) {
            return documentWorkflowRepository.findByCurrentStep(status);
        }
        return documentWorkflowRepository.findAll();
    }

    private void logTransition(UUID documentId, UUID requestUid, WorkflowStatus previousStep,
                               WorkflowStatus currentStep, long durationMs, String message, String errorReason) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("timestamp", Instant.now().toString());
        logData.put("level", errorReason != null ? "WARN" : "INFO");
        logData.put("logger", "workflow.transition");
        logData.put("documentId", documentId != null ? documentId.toString() : null);
        logData.put("requestUid", requestUid != null ? requestUid.toString() : null);
        logData.put("previousStep", previousStep != null ? previousStep.name() : null);
        logData.put("currentStep", currentStep != null ? currentStep.name() : null);
        logData.put("durationMs", durationMs);
        logData.put("message", message);
        if (errorReason != null) {
            logData.put("errorReason", errorReason);
        }
        try {
            String json = objectMapper.writeValueAsString(logData);
            if (errorReason != null) {
                transitionLog.warn(json);
            } else {
                transitionLog.info(json);
            }
        } catch (JsonProcessingException e) {
            transitionLog.warn("Failed to serialize transition log: documentId={} {} -> {}",
                documentId, previousStep, currentStep, e);
        }
    }
}