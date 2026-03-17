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

CREATE INDEX idx_step_result_status
    ON step_result (status);