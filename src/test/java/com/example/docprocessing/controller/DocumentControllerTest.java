package com.example.docprocessing.controller;

import com.example.docprocessing.domain.DocumentWorkflow;
import com.example.docprocessing.domain.WorkflowStatus;
import com.example.docprocessing.service.PipelineOrchestrator;
import com.example.docprocessing.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowService workflowService;

    @MockitoBean
    private PipelineOrchestrator pipelineOrchestrator;

    @Test
    void createDocument_returns202_and_starts_processing() throws Exception {
        UUID docRef = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        UUID documentId = UUID.randomUUID();
        UUID requestUid = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-06T10:30:00Z");

        DocumentWorkflow wf = new DocumentWorkflow();
        wf.setId(documentId);
        wf.setDocRef(docRef);
        wf.setRequestUid(requestUid);
        wf.setCurrentStep(WorkflowStatus.RECEIVED);
        wf.setCreatedAt(createdAt);

        when(workflowService.createWorkflow(eq(docRef), anyString(), anyLong())).thenReturn(wf);
        doNothing().when(pipelineOrchestrator).startProcessing(documentId);

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "docRef": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.docRef").value(docRef.toString()))
            .andExpect(jsonPath("$.requestUid").value(requestUid.toString()))
            .andExpect(jsonPath("$.currentStep").value("RECEIVED"))
            .andExpect(jsonPath("$.createdAt").value("2026-03-06T10:30:00Z"));

        verify(pipelineOrchestrator).startProcessing(documentId);
    }
}