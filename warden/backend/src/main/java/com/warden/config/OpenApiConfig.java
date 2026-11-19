package com.warden.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html with an "Authorize" button for the Bearer token. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Warden API", version = "v1",
                description = "Authentication & user management. Login: POST /api/auth/login, then Authorize with the accessToken."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
