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

CREATE INDEX idx_document_workflow_current_step
    ON document_workflow (current_step);