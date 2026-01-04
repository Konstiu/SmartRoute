package com.smartroute.smartroute1.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;


@OpenAPIDefinition(
        info = @Info(
                description = "OpenApi documentation for SMARTRoute",
                title = "SMARTRoute Documentation",
                version = "v1",
                contact = @Contact(
                        name = "SmartRoute Team",
                        email = "konstantin.unterweger@gmail.com, e12514056@student.tuwien.ac.at, e12216466@student.tuwien.ac.at, e12223229@student.tuwien.ac.at, e12216445@student.tuwien.ac.at, e11825345@student.tuwien.ac.at"
                )
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT authentication token",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
@Configuration
public class OpenApiConfig {}