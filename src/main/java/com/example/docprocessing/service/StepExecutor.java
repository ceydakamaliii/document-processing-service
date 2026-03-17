package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

    private final WorkflowService workflowService;

    public StepExecutor(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public void executeStep(UUID documentId,
                            WorkflowStatus processingStatus,
                            WorkflowStatus successStatus,
                            WorkflowStatus failedStatus,
                            ProcessingStep step,
                            Supplier<String> stepLogic) {

        workflowService.transitionTo(documentId, processingStatus);

        Instant startedAt = Instant.now();
        workflowService.saveStepResult(
            documentId,
            step,
            StepStatus.PROCESSING,
            startedAt,
            null,
            null,
            null,
            null
        );

        try {
            String resultJson = stepLogic.get();

            Instant completedAt = Instant.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            workflowService.saveStepResult(
                documentId,
                step,
                StepStatus.COMPLETED,
                startedAt,
                completedAt,
                durationMs,
                resultJson,
                null
            );
            workflowService.transitionTo(documentId, successStatus);

        } catch (Exception e) {
            log.warn("Step {} failed for documentId={}", step, documentId, e);

            Instant completedAt = Instant.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();

            workflowService.saveStepResult(
                documentId,
                step,
                StepStatus.FAILED,
                startedAt,
                completedAt,
                durationMs,
                null,
                e.getMessage()
            );
            workflowService.transitionTo(documentId, failedStatus);
            workflowService.transitionTo(documentId, WorkflowStatus.FAILED);
        }
    }
}