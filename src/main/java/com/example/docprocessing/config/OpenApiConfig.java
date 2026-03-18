package com.example.docprocessing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentProcessingOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Document Processing Orchestration Service API")
                .description("REST API for document processing workflow orchestration")
                .version("1.0.0"));
    }
}
