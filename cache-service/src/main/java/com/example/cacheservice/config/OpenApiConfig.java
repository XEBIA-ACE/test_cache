package com.example.cacheservice.config;

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
 * SpringDoc / OpenAPI 3 configuration.
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html} and the raw
 * OpenAPI spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI cacheServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cache Service API")
                        .description("""
                                Production-grade cache service built on Redis.

                                **Supported Redis Topologies**
                                - Standalone – single node
                                - Sentinel   – automatic failover (HA)
                                - Cluster    – horizontal sharding

                                **Client Libraries**
                                - **Lettuce** – primary client for all CRUD/Hash/Counter/Scan operations
                                - **Redisson** – distributed locks, rate-limiting, and pub/sub
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("platform@example.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local development"),
                        new Server().url("https://cache-service.staging.example.com").description("Staging"),
                        new Server().url("https://cache-service.prod.example.com").description("Production")
                ));
    }
}
