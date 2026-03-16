package com.example.docprocessing.domain;              

import jakarta.persistence.Entity;                      
import jakarta.persistence.Id;                          
import jakarta.persistence.EnumType;                    
import jakarta.persistence.Enumerated;                  
import lombok.AllArgsConstructor;                       
import lombok.Data;                                     
import lombok.NoArgsConstructor;                        
import java.time.Instant;                               
import java.util.UUID;                             

@Entity                                                 
@Data                                                   
@NoArgsConstructor                                      
@AllArgsConstructor                                     
public class DocumentWorkflow {                         

    @Id                                                
    private UUID id;                                   

    private UUID docRef;                              
    private UUID requestUid;                          
    private String documentName;                      
    private Long contentSizeBytes;                    

    @Enumerated(EnumType.STRING)                     
    private WorkflowStatus currentStep;               

    @Enumerated(EnumType.STRING)                     
    private WorkflowStatus failedAtStep;             

    private String failureReason;                     

    private Integer retryCount;                       

    private Instant createdAt;                        
    private Instant updatedAt;                        
}