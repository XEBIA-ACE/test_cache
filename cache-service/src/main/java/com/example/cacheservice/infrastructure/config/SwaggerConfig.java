package com.example.cacheservice.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 documentation configuration.
 *
 * <p>Swagger UI is available at: {@code http://localhost:8080/swagger-ui.html}
 * <p>OpenAPI spec is available at: {@code http://localhost:8080/v3/api-docs}
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(List.of(
                new Server().url("http://localhost:" + serverPort).description("Local Development"),
                new Server().url("https://cache-service.staging.example.com").description("Staging"),
                new Server().url("https://cache-service.example.com").description("Production")
            ))
            .components(new Components());
    }

    private Info apiInfo() {
        return new Info()
            .title("Cache Service API")
            .description("""
                RESTful API for Redis cache operations.

                **Supported Redis topologies:**
                - `standalone` – Single Redis instance (development)
                - `sentinel`   – Redis Sentinel for high-availability
                - `cluster`    – Redis Cluster for horizontal scaling

                **Features:**
                - Full CRUD cache operations with TTL management
                - Bulk get/set/delete operations
                - Pattern-based key scanning (cluster-safe)
                - Distributed lock management via Redisson
                - Prometheus metrics at `/actuator/prometheus`
                - Health checks at `/actuator/health`
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("Platform Team")
                .email("platform@example.com"))
            .license(new License()
                .name("MIT")
                .url("https://opensource.org/licenses/MIT"));
    }
}
