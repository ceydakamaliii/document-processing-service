package com.example.docprocessing.domain; 

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;       
import lombok.Data;                     
import lombok.NoArgsConstructor;        
import java.time.Instant;               
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity                                 
@Data                                  
@NoArgsConstructor                      
@AllArgsConstructor                    
public class StepResult {

    @Id                                
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                   

    @ManyToOne
    @JoinColumn(name = "document_id")
    private DocumentWorkflow documentWorkflow;

    @Enumerated(EnumType.STRING)        
    private ProcessingStep step;       

    @Enumerated(EnumType.STRING)       
    private StepStatus status;         

    private Instant startedAt;          
    private Instant completedAt;        

    private Long durationMs;            
    private String resultJson;          

    private String errorMessage;        
}