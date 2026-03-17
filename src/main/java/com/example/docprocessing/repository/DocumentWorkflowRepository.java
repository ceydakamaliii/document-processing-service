package com.example.docprocessing.repository;

import com.example.docprocessing.domain.DocumentWorkflow;
import com.example.docprocessing.domain.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentWorkflowRepository extends JpaRepository<DocumentWorkflow, UUID> {

    List<DocumentWorkflow> findByCurrentStep(WorkflowStatus status);
}