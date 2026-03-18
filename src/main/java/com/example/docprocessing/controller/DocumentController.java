package com.example.docprocessing.controller;

import com.example.docprocessing.controller.dto.CancelDocumentResponse;
import com.example.docprocessing.controller.dto.CreateDocumentRequest;
import com.example.docprocessing.controller.dto.DocumentCreatedResponse;
import com.example.docprocessing.controller.dto.DocumentStatusResponse;
import com.example.docprocessing.controller.dto.DocumentSummaryResponse;
import com.example.docprocessing.controller.dto.RetryDocumentResponse;
import com.example.docprocessing.controller.dto.StepResultDto;
import com.example.docprocessing.domain.DocumentWorkflow;
import com.example.docprocessing.domain.ProcessingStep;
import com.example.docprocessing.domain.StepResult;
import com.example.docprocessing.domain.WorkflowStatus;
import com.example.docprocessing.service.PipelineOrchestrator;
import com.example.docprocessing.service.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final WorkflowService workflowService;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final ObjectMapper objectMapper;

    public DocumentController(WorkflowService workflowService,
                              PipelineOrchestrator pipelineOrchestrator,
                              ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<DocumentCreatedResponse> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        DocumentWorkflow wf = workflowService.createWorkflow(
            request.docRef(),
            request.docRef().toString(),
            0L
        );

        pipelineOrchestrator.startProcessing(wf.getId());

        DocumentCreatedResponse response = new DocumentCreatedResponse(
            wf.getId(),
            wf.getDocRef(),
            wf.getRequestUid(),
            wf.getCurrentStep(),
            wf.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("")
    public ResponseEntity<List<DocumentSummaryResponse>> listDocuments(
            @RequestParam(required = false) String status) {
        
        WorkflowStatus workflowStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                workflowStatus = WorkflowStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid status: " + status + ". Valid values: " + 
                    String.join(", ", java.util.Arrays.stream(WorkflowStatus.values())
                        .map(Enum::name)
                        .toArray(String[]::new)));
            }
        }
        
        List<DocumentWorkflow> workflows = workflowService.listDocuments(workflowStatus);
        
        List<DocumentSummaryResponse> response = workflows.stream()
            .map(wf -> DocumentSummaryResponse.builder()
                .documentId(wf.getId())
                .docRef(wf.getDocRef())
                .documentName(wf.getDocumentName())
                .currentStep(wf.getCurrentStep().name())
                .failedAtStep(wf.getFailedAtStep() != null ? wf.getFailedAtStep().name() : null)
                .createdAt(wf.getCreatedAt())
                .updatedAt(wf.getUpdatedAt())
                .build())
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(@PathVariable UUID documentId) {
        DocumentWorkflow workflow = workflowService.getById(documentId);
        Map<ProcessingStep, StepResult> stepResults = workflowService.getAllStepResults(documentId);

        Map<String, StepResultDto> stepResultsMap = new java.util.LinkedHashMap<>();
        
        if (stepResults.containsKey(ProcessingStep.DMS_FETCH)) {
            stepResultsMap.put("dmsFetch", mapToStepResultDto(stepResults.get(ProcessingStep.DMS_FETCH), true));
        }
        
        if (stepResults.containsKey(ProcessingStep.OCR)) {
            stepResultsMap.put("ocr", mapToStepResultDto(stepResults.get(ProcessingStep.OCR), false));
        }
        
        if (stepResults.containsKey(ProcessingStep.CLASSIFICATION)) {
            stepResultsMap.put("classification", mapToStepResultDto(stepResults.get(ProcessingStep.CLASSIFICATION), false));
        }
        
        if (stepResults.containsKey(ProcessingStep.NER)) {
            stepResultsMap.put("ner", mapToStepResultDto(stepResults.get(ProcessingStep.NER), false));
        }

        DocumentStatusResponse response = DocumentStatusResponse.builder()
            .documentId(workflow.getId())
            .docRef(workflow.getDocRef())
            .requestUid(workflow.getRequestUid())
            .documentName(workflow.getDocumentName())
            .contentSizeBytes(workflow.getContentSizeBytes())
            .currentStep(workflow.getCurrentStep().name())
            .failedAtStep(workflow.getFailedAtStep() != null ? workflow.getFailedAtStep().name() : null)
            .failureReason(workflow.getFailureReason())
            .stepResults(stepResultsMap)
            .createdAt(workflow.getCreatedAt())
            .updatedAt(workflow.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/steps/{stepName}")
    public ResponseEntity<StepResultDto> getStepResult(
            @PathVariable UUID documentId,
            @PathVariable String stepName) {
        
        workflowService.getById(documentId);
        
        ProcessingStep step = parseStepName(stepName);
        
        StepResult stepResult = workflowService.getStepResult(documentId, step)
            .orElseThrow(() -> new com.example.docprocessing.exception.StepNotFoundException(
                "Step '" + stepName + "' has not been executed yet for document " + documentId));
        
        boolean isDmsFetch = step == ProcessingStep.DMS_FETCH;
        StepResultDto dto = mapToStepResultDto(stepResult, isDmsFetch);
        
        return ResponseEntity.ok(dto);
    }

    private ProcessingStep parseStepName(String stepName) {
        switch (stepName.toLowerCase()) {
            case "dms-fetch":
            case "dmsfetch":
                return ProcessingStep.DMS_FETCH;
            case "ocr":
                return ProcessingStep.OCR;
            case "classification":
            case "classify":
                return ProcessingStep.CLASSIFICATION;
            case "ner":
                return ProcessingStep.NER;
            default:
                throw new IllegalArgumentException(
                    "Invalid step name: " + stepName + ". Valid values: dms-fetch, ocr, classification, ner");
        }
    }

    @PostMapping("/{documentId}/cancel")
    public ResponseEntity<CancelDocumentResponse> cancelDocument(@PathVariable UUID documentId) {
        DocumentWorkflow workflow = workflowService.getById(documentId);
        String previousStep = workflow.getCurrentStep().name();
        
        DocumentWorkflow cancelled = workflowService.cancel(documentId);
        
        CancelDocumentResponse response = CancelDocumentResponse.builder()
            .documentId(cancelled.getId())
            .previousStep(previousStep)
            .currentStep(cancelled.getCurrentStep().name())
            .reason(cancelled.getFailureReason())
            .cancelledAt(cancelled.getUpdatedAt())
            .build();
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/retry")
    public ResponseEntity<RetryDocumentResponse> retryDocument(@PathVariable UUID documentId) {
        DocumentWorkflow workflow = workflowService.getById(documentId);
        String restartedFromStep = workflow.getFailedAtStep() != null 
            ? workflow.getFailedAtStep().name() 
            : "UNKNOWN";
        
        DocumentWorkflow retried = workflowService.retry(documentId);
        
        pipelineOrchestrator.startProcessing(documentId);
        
        RetryDocumentResponse response = RetryDocumentResponse.builder()
            .documentId(retried.getId())
            .restartedFromStep(restartedFromStep)
            .currentStep(retried.getCurrentStep().name())
            .retryCount(retried.getRetryCount())
            .build();
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private StepResultDto mapToStepResultDto(StepResult stepResult, boolean isDmsFetch) {
        JsonNode resultNode = null;
        if (stepResult.getResultJson() != null) {
            try {
                JsonNode fullNode = objectMapper.readTree(stepResult.getResultJson());
                
                if (isDmsFetch && fullNode.isObject()) {
                    com.fasterxml.jackson.databind.node.ObjectNode objNode = 
                        (com.fasterxml.jackson.databind.node.ObjectNode) fullNode;
                    objNode.remove("contentBase64");
                    resultNode = objNode;
                } else {
                    resultNode = fullNode;
                }
            } catch (Exception e) {
                log.warn("Failed to parse step result JSON: {}", e.getMessage());
            }
        }

        return StepResultDto.builder()
            .status(stepResult.getStatus().name())
            .startedAt(stepResult.getStartedAt())
            .completedAt(stepResult.getCompletedAt())
            .durationMs(stepResult.getDurationMs())
            .result(resultNode)
            .errorMessage(stepResult.getErrorMessage())
            .build();
    }
}
