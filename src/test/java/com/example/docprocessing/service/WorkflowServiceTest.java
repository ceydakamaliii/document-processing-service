package com.example.docprocessing.service;

import com.example.docprocessing.domain.DocumentWorkflow;
import com.example.docprocessing.domain.WorkflowStatus;
import com.example.docprocessing.exception.InvalidTransitionException;
import com.example.docprocessing.repository.DocumentWorkflowRepository;
import com.example.docprocessing.repository.StepResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private DocumentWorkflowRepository documentWorkflowRepository;

    @Mock
    private StepResultRepository stepResultRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WorkflowService workflowService;

    @Test
    void retry_from_ocr_failed_resumes_from_ocr_processing() {
        UUID documentId = UUID.randomUUID();

        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setId(documentId);
        wf.setCurrentStep(WorkflowStatus.FAILED);
        wf.setFailedAtStep(WorkflowStatus.OCR_FAILED);
        wf.setRetryCount(1);
        wf.setUpdatedAt(Instant.now());

        when(documentWorkflowRepository.findById(documentId)).thenReturn(Optional.of(wf));
        when(documentWorkflowRepository.save(any(DocumentWorkflow.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        DocumentWorkflow result = workflowService.retry(documentId);

        assertEquals(WorkflowStatus.OCR_PROCESSING, result.getCurrentStep());
        assertEquals(2, result.getRetryCount());
        verify(documentWorkflowRepository).save(wf);
    }

    @Test
    void transitionTo_invalid_transition_throws_conflict_exception() {
        UUID documentId = UUID.randomUUID();

        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setId(documentId);
        wf.setCurrentStep(WorkflowStatus.RECEIVED);
        wf.setUpdatedAt(Instant.now());

        when(documentWorkflowRepository.findById(documentId)).thenReturn(Optional.of(wf));

        assertThrows(
            InvalidTransitionException.class,
            () -> workflowService.transitionTo(documentId, WorkflowStatus.OCR_PROCESSING)
        );

        verify(documentWorkflowRepository, never()).save(any());
    }
}