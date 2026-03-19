# Document Processing Orchestration Service

A Spring Boot 3 / Java 17 service that orchestrates document processing through a multi-step pipeline:

**DMS Fetch -> OCR -> Classification -> NER**

The service tracks workflow state transitions, persists step-level results, supports retry/cancel operations, exposes REST APIs, provides Swagger/OpenAPI docs, and runs with Docker + PostgreSQL.

---

## 1. Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Actuator
- Flyway (database migrations)
- PostgreSQL
- Lombok
- Springdoc OpenAPI (Swagger UI)
- JUnit 5 + Mockito + Spring Test (MockMvc)

---

## 2. Why PostgreSQL?

PostgreSQL is used because this case requires:

- Reliable transactional writes for workflow transitions and step results
- A clean relational model for `document_workflow` and `step_result`
- Migration/versioned schema management via Flyway

---

## 3. Core Workflow

Each submitted document is processed in order:

1. DMS Fetch (metadata + content)
2. OCR
3. Classification
4. NER

Workflow status is stored in `DocumentWorkflow`, and each step execution is stored in `StepResult`.

### Workflow Status (high level)
- `RECEIVED`
- `DMS_FETCHING`, `DMS_FETCH_COMPLETED`, `DMS_FETCH_FAILED`
- `OCR_PROCESSING`, `OCR_COMPLETED`, `OCR_FAILED`
- `CLASSIFYING`, `CLASSIFICATION_COMPLETED`, `CLASSIFICATION_FAILED`
- `NER_PROCESSING`, `NER_COMPLETED`, `NER_FAILED`
- `COMPLETED`
- `FAILED`

### Step Status
- `PENDING -> PROCESSING -> COMPLETED / FAILED`

---

## 4. Request Execution Flow (POST /api/v1/documents)

When a document is submitted:

1. `DocumentController#createDocument(...)` receives request
2. `WorkflowService#createWorkflow(...)` persists initial workflow in `RECEIVED`
3. `PipelineOrchestrator#startProcessing(documentId)` starts asynchronous processing
4. Steps are executed in sequence using `StepExecutor`
5. `WorkflowService#transitionTo(...)` records state changes (with structured logs)
6. `WorkflowService#saveStepResult(...)` persists step outputs/errors

---

## 5. REST API Endpoints

Base path: `/api/v1/documents`

- `POST /api/v1/documents`  
  Submit document for processing (returns `202 Accepted`)

- `GET /api/v1/documents/{documentId}`  
  Get full workflow status and step results

- `GET /api/v1/documents/{documentId}/steps/{stepName}`  
  Get a specific step result (`dms-fetch`, `ocr`, `classification`, `ner`)

- `POST /api/v1/documents/{documentId}/retry`  
  Retry from failed step (if workflow is in `FAILED`)

- `POST /api/v1/documents/{documentId}/cancel`  
  Cancel processing (non-terminal states)

- `GET /api/v1/documents?status={WORKFLOW_STATUS}`  
  List documents (optionally filtered by status)

---

## 6. Swagger / OpenAPI

Swagger is enabled.

From configuration:

- API Docs path: `/api-docs`
- Swagger UI path: `/swagger-ui.html`

### URLs (local)
- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

If running on another mapped port, replace `8080` accordingly.

---

## 7. Health / Observability

Actuator is enabled with health endpoint exposure.

### Health endpoint
- [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Configured with DB health check enabled.

Structured transition logs are emitted with logger name:
- `workflow.transition`

---

## 8. Configuration

Main runtime config is in:

- `src/main/resources/application.yaml`

Includes:
- datasource (PostgreSQL)
- JPA settings
- DMS settings
- actuator health exposure
- springdoc paths

---

## 9. Run Locally (without Docker)

```bash
./mvnw spring-boot:run
```

---

## 10. Run with Docker

```bash
docker compose up --build -d
```

Stop services:
```bash
docker compose down
```

Clean reset (including volumes):
```bash
docker compose down -v
docker compose up --build -d
```

---

## 11. Example cURL Commands

### Submit document
```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{"docRef":"f47ac10b-58cc-4372-a567-0e02b2c3d479"}'
```

### Get document status
```bash
curl http://localhost:8080/api/v1/documents/{documentId}
```

### Get OCR step result
```bash
curl http://localhost:8080/api/v1/documents/{documentId}/steps/ocr
```

### Retry document
```bash
curl -X POST http://localhost:8080/api/v1/documents/{documentId}/retry
```

### Cancel document
```bash
curl -X POST http://localhost:8080/api/v1/documents/{documentId}/cancel
```

### Health
```bash
curl http://localhost:8080/actuator/health
```

---

## 12. Testing

### Run all tests
```bash
./mvnw test
```

Current test coverage includes:
- Spring context smoke test
- Controller smoke test (`POST /documents` -> `202`)
- Workflow service unit tests (retry + invalid transition)
- Step executor unit tests (success/failure paths)

---

## 13. Notes / Assumptions

- Pipeline uses simulated DMS/OCR/Classification/NER services for assignment scope.
- Failures can be simulated and are persisted in workflow/step records.
- Retry resumes from the failed step according to workflow state rules.
- Invalid transitions are rejected to enforce state machine integrity.
