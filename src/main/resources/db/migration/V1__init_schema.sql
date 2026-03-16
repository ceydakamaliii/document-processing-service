CREATE TABLE document_workflow (
    id UUID PRIMARY KEY,                         

    doc_ref UUID NOT NULL,                       
    request_uid UUID NOT NULL,                   
    document_name VARCHAR(255),                 
    content_size_bytes BIGINT,                   

    current_step VARCHAR(50) NOT NULL,           
    failed_at_step VARCHAR(50),                
    failure_reason TEXT,                        

    retry_count INT DEFAULT 0,                   
    created_at TIMESTAMPTZ,                      
    updated_at TIMESTAMPTZ                       
);

CREATE TABLE step_result (
    id BIGSERIAL PRIMARY KEY,                    

    document_id UUID NOT NULL,            
    step VARCHAR(50) NOT NULL,                  
    status VARCHAR(50) NOT NULL,                

    started_at TIMESTAMPTZ,                      
    completed_at TIMESTAMPTZ,                    
    duration_ms BIGINT,                         

    result_json TEXT,                           
    error_message TEXT                           
);

ALTER TABLE step_result
    ADD CONSTRAINT fk_step_result_document
    FOREIGN KEY (document_id)
    REFERENCES document_workflow(id);

CREATE INDEX idx_step_result_document_id
    ON step_result (document_id);

CREATE INDEX idx_document_workflow_current_step
    ON document_workflow (current_step);

CREATE INDEX idx_step_result_status
    ON step_result (status);