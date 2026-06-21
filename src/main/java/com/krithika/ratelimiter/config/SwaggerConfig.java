package com.krithika.ratelimiter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Rate Limiter Service")
                        .version("1.0.0")
                        .description("""
                                A production-grade API Rate Limiter microservice featuring:
                                - Token Bucket algorithm (burst-friendly)
                                - Sliding Window algorithm (strict)
                                - API Key authentication (stored in Supabase/PostgreSQL)
                                - Real-time usage analytics
                                - Redis-backed counters for high-throughput
                                - Configurable per-client limits
                                
                                Pass your API key via the `X-API-Key` header on all requests.
                                """)
                        .contact(new Contact()
                                .name("Krithika Shetty")
                                .email("krithikashettyy@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")));
    }
}
