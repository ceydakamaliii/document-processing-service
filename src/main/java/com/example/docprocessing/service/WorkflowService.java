package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import com.example.docprocessing.exception.DocumentNotFoundException;
import com.example.docprocessing.exception.InvalidTransitionException;
import com.example.docprocessing.repository.DocumentWorkflowRepository;
import com.example.docprocessing.repository.StepResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

@Service
public class WorkflowService {

    private final DocumentWorkflowRepository documentWorkflowRepository;
    private final StepResultRepository stepResultRepository;

    public WorkflowService(DocumentWorkflowRepository documentWorkflowRepository,
                           StepResultRepository stepResultRepository) {
        this.documentWorkflowRepository = documentWorkflowRepository;
        this.stepResultRepository = stepResultRepository;
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
        DocumentWorkflow wf = getById(documentId);
        WorkflowStatus current = wf.getCurrentStep();

        if (!isValidTransition(current, newStatus)) {
            throw new InvalidTransitionException("Invalid transition: " + current + " -> " + newStatus);
        }

        wf.setCurrentStep(newStatus);
        if (isFailureStatus(newStatus)) {
            wf.setFailedAtStep(current);
        }
        wf.setUpdatedAt(Instant.now());
        return documentWorkflowRepository.save(wf);
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

        wf.setUpdatedAt(Instant.now());
        return documentWorkflowRepository.save(wf);
    }

    @Transactional
    public DocumentWorkflow cancel(UUID documentId) {
        DocumentWorkflow wf = getById(documentId);

        if (wf.getCurrentStep() == WorkflowStatus.COMPLETED || wf.getCurrentStep() == WorkflowStatus.FAILED) {
            throw new InvalidTransitionException("Cannot cancel a terminal workflow: " + wf.getCurrentStep());
        }

        wf.setFailedAtStep(wf.getCurrentStep());
        wf.setCurrentStep(WorkflowStatus.FAILED);
        wf.setFailureReason("CANCELLED_BY_USER");
        wf.setUpdatedAt(Instant.now());
        return documentWorkflowRepository.save(wf);
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
}