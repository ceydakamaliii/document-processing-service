package com.example.docprocessing.service;

import com.example.docprocessing.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepExecutorTest {

    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private StepExecutor stepExecutor;

    @Test
    void executeStep_success_path() {
        UUID documentId = UUID.randomUUID();

        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setCurrentStep(WorkflowStatus.OCR_PROCESSING);
        when(workflowService.getById(documentId)).thenReturn(wf);

        stepExecutor.executeStep(
            documentId,
            WorkflowStatus.OCR_PROCESSING,
            WorkflowStatus.OCR_COMPLETED,
            WorkflowStatus.OCR_FAILED,
            ProcessingStep.OCR,
            () -> "{\"ok\":true}"
        );

        verify(workflowService).getById(documentId);
        verify(workflowService, never()).transitionTo(documentId, WorkflowStatus.OCR_PROCESSING);

        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.OCR), eq(StepStatus.PENDING),
            any(), isNull(), isNull(), isNull(), isNull()
        );
        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.OCR), eq(StepStatus.PROCESSING),
            any(), isNull(), isNull(), isNull(), isNull()
        );
        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.OCR), eq(StepStatus.COMPLETED),
            any(), any(), anyLong(), eq("{\"ok\":true}"), isNull()
        );

        verify(workflowService).transitionTo(documentId, WorkflowStatus.OCR_COMPLETED);
        verify(workflowService, never()).transitionTo(documentId, WorkflowStatus.OCR_FAILED, "boom");
        verify(workflowService, never()).transitionTo(documentId, WorkflowStatus.FAILED);
    }

    @Test
    void executeStep_failure_path() {
        UUID documentId = UUID.randomUUID();

        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setCurrentStep(WorkflowStatus.CLASSIFICATION_COMPLETED); // processingStatus'ten farklı olsun
        when(workflowService.getById(documentId)).thenReturn(wf);

        RuntimeException ex = new RuntimeException("boom");

        stepExecutor.executeStep(
            documentId,
            WorkflowStatus.CLASSIFYING,
            WorkflowStatus.CLASSIFICATION_COMPLETED,
            WorkflowStatus.CLASSIFICATION_FAILED,
            ProcessingStep.CLASSIFICATION,
            () -> { throw ex; }
        );

        verify(workflowService).transitionTo(documentId, WorkflowStatus.CLASSIFYING);

        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.CLASSIFICATION), eq(StepStatus.PENDING),
            any(), isNull(), isNull(), isNull(), isNull()
        );
        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.CLASSIFICATION), eq(StepStatus.PROCESSING),
            any(), isNull(), isNull(), isNull(), isNull()
        );
        verify(workflowService).saveStepResult(
            eq(documentId), eq(ProcessingStep.CLASSIFICATION), eq(StepStatus.FAILED),
            any(), any(), anyLong(), isNull(), eq("boom")
        );

        verify(workflowService).transitionTo(documentId, WorkflowStatus.CLASSIFICATION_FAILED, "boom");
        verify(workflowService).transitionTo(documentId, WorkflowStatus.FAILED);
    }
}